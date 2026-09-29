package dev.golem.harness.cli;

import dev.golem.harness.api.Client;
import dev.golem.harness.api.HarnessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Builds the launch spec, prepares the run directory, starts the JVM and waits for the listener. */
public final class Launcher {

    /** Options for one {@code up}. */
    public static final class Options {
        public boolean build = true;
        public boolean sodium = true;
        public boolean show = false;
        public String heap = "2G";
        public String initialHeap = "512M";
        public String world = Config.get().world;
        /** "flat" (default superflat world) or "seeded" (default preset, fixed seed, world harness_seeded). */
        public String worldType = "flat";
        public int width = 1280;
        public int height = 720;
        public int guiScale = 2;
        public int readyTimeoutSeconds = 420;
        public boolean verbose = false;
    }

    private Launcher() {}

    static String stamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    /** Captures (or reuses) the launch spec for a node. */
    public static LaunchSpec spec(Nodes.Node node, boolean build, Path log) throws IOException {
        Path file = Paths.specFile(node);
        if (build || !Files.exists(file)) {
            if (node.fabric()) {
                // Loom refuses to remap a Golem Core jar older than its sources; build it first. `build`,
                // not `jar`: Loom's `jar` writes a dev jar under devlibs, and the guard (and the composite
                // build) look at the remapped jar under libs.
                System.out.println("[harness] " + node.name() + ": building Golem Core");
                int code = Gradle.run(Paths.golemCore(), log, ":" + node.name() + ":build", "-x", "test", "-q");
                if (code != 0) throw new HarnessException("Golem Core build failed for " + node.name() + "; see " + log);
            }
            System.out.println("[harness] " + node.name() + ": building and capturing the client launch");
            int code = Gradle.run(Paths.mod(), log, ":" + node.name() + ":runClient",
                    "-Pharness.spec=" + file.toAbsolutePath(), "-q");
            if (code != 0) throw new HarnessException("launch capture failed for " + node.name() + "; see " + log);
            if (!Files.exists(file)) throw new HarnessException("gradle finished but wrote no spec at " + file);
        }
        return LaunchSpec.read(file);
    }

    /** Mirrors the dev client run directory into {@code runs/harness} and writes the harness options. */
    public static void prepareRunDir(Nodes.Node node, Options options) throws IOException {
        Path run = Paths.runDir(node);
        Path client = Paths.clientRunDir(node);
        Files.createDirectories(run);
        // Mods: exact mirror of the dev client's extra jars, minus the Sodium family on request.
        Path mods = run.resolve("mods");
        Files.createDirectories(mods);
        List<String> wanted = new ArrayList<>();
        Path clientMods = client.resolve("mods");
        if (Files.isDirectory(clientMods)) {
            try (Stream<Path> files = Files.list(clientMods)) {
                for (Path jar : files.toList()) {
                    String name = jar.getFileName().toString();
                    if (!name.endsWith(".jar")) continue;
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (!options.sodium && (lower.contains("sodium") || lower.contains("embeddium") || lower.contains("indium"))) continue;
                    wanted.add(name);
                    Path target = mods.resolve(name);
                    if (!Files.exists(target) || Files.size(target) != Files.size(jar)) {
                        Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        try (Stream<Path> files = Files.list(mods)) {
            for (Path jar : files.toList()) {
                if (jar.getFileName().toString().endsWith(".jar") && !wanted.contains(jar.getFileName().toString())) {
                    Files.delete(jar);
                }
            }
        }
        // Config: seeded once from the dev client so JEI, Sodium and the mod start with the same settings.
        Path config = run.resolve("config");
        if (!Files.isDirectory(config) && Files.isDirectory(client.resolve("config"))) {
            copyTree(client.resolve("config"), config);
        }
        Files.createDirectories(config);
        if (node.forgeLike()) {
            // No early loading window: it is a second GLFW window that would take focus before ours.
            Path fml = config.resolve("fml.toml");
            String current = Files.exists(fml) ? Files.readString(fml, StandardCharsets.UTF_8) : "";
            if (!current.contains("earlyWindowControl=false")) {
                String updated = current.replaceAll("(?m)^earlyWindowControl\\s*=.*$", "").trim();
                updated = (updated.isEmpty() ? "" : updated + "\n") + "earlyWindowControl=false\n";
                Files.writeString(fml, updated, StandardCharsets.UTF_8);
            }
        }
        // Options: rewritten every launch so a scenario's set_option never leaks into the next run.
        String template = optionsTemplate();
        Files.writeString(run.resolve("options.txt"), template.replace("${guiScale}", Integer.toString(options.guiScale)),
                StandardCharsets.UTF_8);
        Files.createDirectories(run.resolve("saves"));
        World.syncFixtures(node, options.world);
    }

    /** The config's options template, else the one bundled in the jar. */
    static String optionsTemplate() throws IOException {
        Path override = Config.get().optionsTemplate;
        if (override != null) return Files.readString(override, StandardCharsets.UTF_8);
        try (var in = Launcher.class.getResourceAsStream("/options.txt")) {
            if (in == null) throw new HarnessException("bundled options.txt missing from the harness jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path source : walk.toList()) {
                Path target = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) Files.createDirectories(target);
                else Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Starts the game JVM from the spec. The returned process writes to {@code log}. */
    public static Process launch(Nodes.Node node, LaunchSpec spec, Options options, Path log, boolean quickPlay) throws IOException {
        Path run = Paths.runDir(node);
        List<String> argfile = new ArrayList<>();
        for (String arg : spec.jvmArgs()) {
            if (arg.startsWith("-Xmx") || arg.startsWith("-Xms") || arg.startsWith("-Dgolem.harness.")) continue;
            argfile.add(arg);
        }
        argfile.add("-Xms" + options.initialHeap);
        argfile.add("-Xmx" + options.heap);
        argfile.add("-Dgolem.harness.port=" + node.port());
        argfile.add("-Dgolem.harness.minimize=" + !options.show);
        argfile.add("-cp");
        argfile.add(String.join(java.io.File.pathSeparator, spec.classpath()));
        argfile.add(spec.mainClass());
        argfile.addAll(spec.programArgs());
        argfile.add("--width");
        argfile.add(Integer.toString(options.width));
        argfile.add("--height");
        argfile.add(Integer.toString(options.height));
        if (quickPlay) {
            argfile.add("--quickPlaySingleplayer");
            argfile.add(options.world);
        }
        Path args = run.resolve("harness-launch.args");
        StringBuilder sb = new StringBuilder();
        for (String token : argfile) sb.append(quoteForArgfile(token)).append('\n');
        Files.writeString(args, sb.toString(), StandardCharsets.UTF_8);

        List<String> command = List.of(spec.javaExecutable(), "@" + args.toAbsolutePath());
        ProcessBuilder builder = new ProcessBuilder(command).directory(run.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        builder.environment().clear();
        builder.environment().putAll(spec.env());
        builder.environment().put("GOLEM_HARNESS_PORT", Integer.toString(node.port()));
        if (options.verbose) System.out.println("[harness] " + node.name() + ": " + String.join(" ", command));
        return builder.start();
    }

    /** JDK @argfile token: double-quoted, backslash and quote escaped. */
    static String quoteForArgfile(String token) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : token.toCharArray()) {
            if (c == '\\' || c == '"') sb.append('\\');
            sb.append(c);
        }
        return sb.append('"').toString();
    }

    /** Waits until the listener answers, then until the client is in a world (creating it if needed). */
    public static void waitReady(Nodes.Node node, Process process, Options options, boolean worldExists) {
        long deadline = System.currentTimeMillis() + options.readyTimeoutSeconds * 1000L;
        Client client = null;
        while (client == null) {
            if (!process.isAlive()) throw new HarnessException(node.name() + " exited with code " + process.exitValue() + " before the listener came up");
            if (System.currentTimeMillis() > deadline) throw new HarnessException(node.name() + " listener did not answer within " + options.readyTimeoutSeconds + " s");
            try {
                client = Client.connect(node.name(), node.port());
                client.ping();
            } catch (RuntimeException e) {
                client = null;
                sleep(2000);
            }
        }
        try (Client ready = client) {
            client = ready;
            // On Fabric the listener answers before the initial resource reload has finished. Creating
            // a world under that overlay hangs vanilla's world-load loop, so wait for a real screen.
            while (true) {
                if (!process.isAlive()) throw new HarnessException(node.name() + " exited during startup");
                if (System.currentTimeMillis() > deadline) throw new HarnessException(node.name() + " never left the loading overlay");
                com.google.gson.JsonObject status;
                try {
                    status = client.status();
                } catch (HarnessException e) {
                    // Starting the integrated server can occupy the render thread longer than one
                    // status request's deadline. The launch still has its own bounded readiness budget.
                    if (!e.getMessage().contains("client thread did not answer in time")) throw e;
                    sleep(1000);
                    continue;
                }
                boolean overlay = !status.get("overlay").isJsonNull();
                boolean screen = !status.get("screen").isJsonNull();
                if (!overlay && (screen || status.get("in_world").getAsBoolean())) break;
                if (!process.isAlive()) throw new HarnessException(node.name() + " exited during startup");
                if (System.currentTimeMillis() > deadline) throw new HarnessException(node.name() + " never left the loading overlay");
                sleep(1000);
            }
            if (!worldExists) {
                boolean seeded = "seeded".equals(options.worldType);
                System.out.println("[harness] " + node.name() + ": creating " + (seeded ? "seeded (seed "
                        + Client.SEEDED_WORLD_SEED + ")" : "superflat") + " world '" + options.world + "'");
                client.createWorld(options.world, options.worldType, Client.SEEDED_WORLD_SEED, seeded ? 420_000L : 240_000L);
                if (seeded) client.freezeWorld();
            } else {
                client.waitInWorld(Math.max(60_000L, deadline - System.currentTimeMillis()));
            }
            var status = client.status();
            System.out.println("[harness] " + node.name() + ": in world (fps " + status.get("fps") + ", heap "
                    + status.get("heap_used_mb") + "/" + status.get("heap_max_mb") + " MB, focused " + status.get("focused") + ")");
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
