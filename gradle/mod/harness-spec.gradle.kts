import org.gradle.jvm.toolchain.JavaToolchainService

/**
 * Launch-spec capture for the Golem Core visual test harness. Shared by every Golem Core mod: apply it
 * from each loader buildscript with
 *
 *     apply(from = rootProject.file("../Golem-Core/gradle/mod/harness-spec.gradle.kts"))
 *
 * `gradlew :<node>:runClient -Pharness.spec=<file>` then runs every preparation step the real client
 * run needs (ModDev's `prepareClientRun` and `syncDevMods`, Loom's remap and jar-freshness guards) and
 * starts the JVM with a one-shot capture main in place of the game's main class. That class writes the
 * executable, JVM arguments, class path, program arguments, environment and working directory to the
 * spec file as JSON and exits. The harness CLI relaunches the game from the spec with no Gradle daemon
 * alive, which is what makes two clients fit beside a working desktop.
 *
 * Reading the JavaExec's fields at configuration time would miss what Loom and ModDev add inside
 * `exec()` (arg files, module paths, the legacy class path file); letting the JVM report on itself
 * does not. The main-class swap happens in `doFirst` so it lands after every plugin and IDE
 * configuration closure; the capture classes are added to the class path at configuration time
 * because Loom finalizes its internal class path before `doFirst` runs.
 *
 * The capture source is embedded below so this script is the only file a mod has to reference. It
 * is compiled with `--release 17` and has no dependencies, so it runs on every node's toolchain.
 */
val harnessSpec = providers.gradleProperty("harness.spec").orNull
if (harnessSpec != null) {
    val javaVersion = (extra.properties["java_version"] as String).toInt()
    val toolchains = extensions.getByType(JavaToolchainService::class.java)
    val captureDir = layout.buildDirectory.dir("harness")
    val captureSource = captureDir.map { it.file("src/dev/golem/harness/capture/CaptureMain.java") }
    val captureClasses = captureDir.map { it.dir("capture") }

    val writeCapture = tasks.register("harnessCaptureSource") {
        val target = captureSource
        outputs.file(target)
        doLast {
            val file = target.get().asFile
            file.parentFile.mkdirs()
            file.writeText(CAPTURE_MAIN_SOURCE)
        }
    }
    val compileCapture = tasks.register("harnessCaptureClasses", JavaCompile::class.java) {
        dependsOn(writeCapture)
        source(captureSource)
        classpath = files()
        destinationDirectory.set(captureClasses)
        options.release.set(17)
        javaCompiler.set(toolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(javaVersion)) })
    }
    // `{node}` is replaced by this project's name, so one invocation can capture several nodes:
    // `gradlew :a:runClient :b:runClient -Pharness.spec=.../versions/{node}/build/harness/spec.json`.
    val specPath = harnessSpec.replace("{node}", project.name)
    val specFile = if (java.io.File(specPath).isAbsolute) java.io.File(specPath)
        else rootProject.file(specPath)
    tasks.matching { it.name == "runClient" }.configureEach {
        val run = this as JavaExec
        dependsOn(compileCapture)
        run.classpath(captureClasses)
        doFirst {
            val realMain = run.mainClass.get()
            run.mainClass.set("dev.golem.harness.capture.CaptureMain")
            run.systemProperty("golem.harness.capture.main", realMain)
            run.systemProperty("golem.harness.capture.out", specFile.absolutePath)
            logger.lifecycle("Capturing the launch of $path to ${specFile.absolutePath} (main class $realMain)")
        }
    }
}

val CAPTURE_MAIN_SOURCE = """
package dev.golem.harness.capture;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** One-shot replacement main class for a node's runClient; see harness-spec.gradle.kts. */
public final class CaptureMain {

    private CaptureMain() {}

    public static void main(String[] args) throws IOException {
        String out = System.getProperty("golem.harness.capture.out");
        String realMain = System.getProperty("golem.harness.capture.main");
        if (out == null || realMain == null) {
            System.err.println("CaptureMain: missing golem.harness.capture.out / golem.harness.capture.main");
            System.exit(2);
        }
        List<String> jvmArgs = new ArrayList<>();
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-Dgolem.harness.capture.")) continue;
            jvmArgs.add(arg);
        }
        List<String> classpath = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path", "").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (entry.isEmpty()) continue;
            if (entry.replace('\\', '/').endsWith("/harness/capture")) continue;
            classpath.add(entry);
        }
        String executable = ProcessHandle.current().info().command().orElse("");
        Map<String, String> env = new TreeMap<>(System.getenv());

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, "javaExecutable", executable).append(",\n");
        field(json, "javaHome", System.getProperty("java.home", "")).append(",\n");
        field(json, "mainClass", realMain).append(",\n");
        field(json, "workingDir", System.getProperty("user.dir", "")).append(",\n");
        json.append("  \"jvmArgs\": ").append(array(jvmArgs)).append(",\n");
        json.append("  \"classpath\": ").append(array(classpath)).append(",\n");
        json.append("  \"programArgs\": ").append(array(List.of(args))).append(",\n");
        json.append("  \"env\": {");
        boolean first = true;
        for (Map.Entry<String, String> e : env.entrySet()) {
            if (!first) json.append(",");
            first = false;
            json.append("\n    ").append(quote(e.getKey())).append(": ").append(quote(e.getValue()));
        }
        json.append("\n  }\n}\n");

        // Written beside the target and moved into place: the harness CLI launches a node as soon as its
        // spec appears while the same Gradle run is still capturing the next node, so it must never
        // observe a half-written file.
        Path path = Paths.get(out).toAbsolutePath();
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, json.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("[harness] launch spec written to " + path.toAbsolutePath());
        System.exit(0);
    }

    private static StringBuilder field(StringBuilder json, String key, String value) {
        return json.append("  ").append(quote(key)).append(": ").append(quote(value));
    }

    private static String array(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\n    ").append(quote(values.get(i)));
        }
        if (!values.isEmpty()) sb.append("\n  ");
        return sb.append("]").toString();
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
"""
