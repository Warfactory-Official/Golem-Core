package dev.golem.harness.cli;

import dev.golem.harness.api.Client;
import dev.golem.harness.api.HarnessException;
import dev.golem.harness.api.Scenario;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Compiles a single-file scenario in memory and runs it against every requested live client. */
public final class Runner {

    public record Result(String node, boolean passed, String message, String stack, long millis, List<String> screenshots,
                         List<String> transcript) {}

    private Runner() {}

    /** Loads the scenario class from a {@code .java} file. */
    public static Scenario load(Path source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new HarnessException("no Java compiler: run the harness on a JDK, not a JRE");
        String code = Files.readString(source, StandardCharsets.UTF_8);
        String className = className(source, code);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        Map<String, ByteArrayOutputStream> classes = new HashMap<>();
        JavaFileManager manager = new ForwardingJavaFileManager<>(standard) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject sibling) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                classes.put(name, bytes);
                return new SimpleJavaFileObject(URI.create("mem:///" + name.replace('.', '/') + kind.extension), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        return bytes;
                    }
                };
            }
        };
        JavaFileObject unit = new SimpleJavaFileObject(URI.create("string:///" + className.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
        String classpath = System.getProperty("java.class.path");
        List<String> options = List.of("-classpath", classpath, "-proc:none", "-Xlint:none");
        boolean ok = compiler.getTask(null, manager, diagnostics, options, null, List.of(unit)).call();
        if (!ok) {
            StringBuilder sb = new StringBuilder("scenario does not compile: " + source + "\n");
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() != Diagnostic.Kind.ERROR) continue;
                sb.append("  line ").append(d.getLineNumber()).append(": ").append(d.getMessage(null)).append('\n');
            }
            throw new HarnessException(sb.toString());
        }
        ClassLoader loader = new ClassLoader(Runner.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                ByteArrayOutputStream bytes = classes.get(name);
                if (bytes == null) throw new ClassNotFoundException(name);
                byte[] b = bytes.toByteArray();
                return defineClass(name, b, 0, b.length);
            }
        };
        try {
            Class<?> type = loader.loadClass(className);
            if (!Scenario.class.isAssignableFrom(type)) {
                throw new HarnessException(className + " does not implement dev.golem.harness.api.Scenario");
            }
            return (Scenario) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new HarnessException("cannot instantiate " + className + ": " + e, e);
        }
    }

    private static String className(Path source, String code) {
        String file = source.getFileName().toString().replaceFirst("\\.java$", "");
        Matcher pkg = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE).matcher(code);
        Matcher cls = Pattern.compile("public\\s+(?:final\\s+)?class\\s+(\\w+)").matcher(code);
        String name = cls.find() ? cls.group(1) : file;
        return pkg.find() ? pkg.group(1) + "." + name : name;
    }

    /** Runs the scenario once per node in parallel; each client gets its own screenshot folder. */
    public static List<Result> run(Scenario scenario, List<Nodes.Node> nodes, State state, Path outDir, boolean echo) {
        Client.flatWorld = Config.get().world;
        Client.worldFiles = new Client.WorldFiles() {
            @Override
            public void snapshot(String node, String world) throws java.io.IOException {
                World.snapshot(Nodes.get(node), world);
            }

            @Override
            public void restore(String node, String world) throws java.io.IOException {
                World.restore(Nodes.get(node), world);
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, nodes.size()));
        List<Future<Result>> futures = new ArrayList<>();
        for (Nodes.Node node : nodes) {
            futures.add(pool.submit(() -> runOne(scenario, node, state, outDir, echo)));
        }
        List<Result> results = new ArrayList<>();
        for (Future<Result> f : futures) {
            try {
                results.add(f.get());
            } catch (Exception e) {
                results.add(new Result("?", false, "runner failure: " + e, stack(e), 0, List.of(), List.of()));
            }
        }
        pool.shutdown();
        return results;
    }

    private static Result runOne(Scenario scenario, Nodes.Node node, State state, Path outDir, boolean echo) {
        long start = System.currentTimeMillis();
        State.Live live = state.get(node.name()).orElse(null);
        if (live == null || !live.alive()) {
            return new Result(node.name(), false, "client not running (harness up --nodes " + node.name() + ")", "", 0, List.of(), List.of());
        }
        Path shots = Client.ensureDir(outDir.resolve(node.name()));
        try (Client client = Client.connect(node.name(), node.port())) {
            client.setScreenshotDir(shots);
            client.echo = echo;
            try {
                scenario.run(client);
                return new Result(node.name(), true, "passed", "", System.currentTimeMillis() - start, client.screenshots(), client.transcript());
            } catch (Throwable t) {
                String message = t instanceof HarnessException ? t.getMessage() : t.toString();
                try {
                    client.screenshot("failure");
                } catch (RuntimeException ignored) {
                    // the client may be gone
                }
                return new Result(node.name(), false, message, stack(t), System.currentTimeMillis() - start, client.screenshots(), client.transcript());
            } finally {
                try {
                    client.releaseAll();
                } catch (RuntimeException ignored) {
                    // best effort
                }
            }
        } catch (HarnessException e) {
            return new Result(node.name(), false, e.getMessage(), stack(e), System.currentTimeMillis() - start, List.of(), List.of());
        }
    }

    static String stack(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
