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
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Builds the launch spec, prepares the run directory, starts the JVM and waits for the listener. */
public final class Launcher {

    /** Options for one {@code up}. */
    public static final class Options {
        /** "auto" (rebuild only when an input is newer than the spec), "always" or "never". */
        public String build = "auto";
        public boolean sodium = true;
        public boolean show = false;
        public String heap = "2G";
        public String initialHeap = "512M";
        public String world = Config.get().world;
        /** "flat" (default superflat world) or "seeded" (default preset, fixed seed, world harness_seeded). */
        public String worldType = "flat";
        /** The offline player name, replacing the dev run's own {@code --username}; null keeps it. */
        public String username = null;
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

    /** Captures (or reuses) the launch spec for one node. */
    public static LaunchSpec spec(Nodes.Node node, boolean build, Path log) throws IOException {
        if (build || !Files.exists(Paths.specFile(node))) {
            Capture capture = Capture.start(List.of(node), log);
            capture.await(node);
            capture.join();
        }
        return LaunchSpec.read(Paths.specFile(node));
    }

    /**
     * One Gradle run that captures the launch spec of several nodes. Gradle pays its JVM start and
     * configuration once instead of once per node, and runs in the background so the caller can
     * launch each client the moment its spec lands ({@link #await}) while the next node still builds.
     * Fabric nodes first build Golem Core (synchronously, one invocation for all of them).
     */
    public static final class Capture {
        private final Map<String, Long> before = new HashMap<>();
        private final Path log;
        private final Thread thread;
        private volatile Integer exitCode;

        private Capture(List<Nodes.Node> nodes, Path log) {
            this.log = log;
            for (Nodes.Node node : nodes) before.put(node.name(), modified(Paths.specFile(node)));
            List<String> args = new ArrayList<>();
            for (Nodes.Node node : nodes) args.add(":" + node.name() + ":runClient");
            String pattern = Paths.mod().resolve("versions").resolve("{node}").resolve("build").resolve("harness")
                    .resolve("spec.json").toAbsolutePath().toString();
            args.add("-Pharness.spec=" + pattern);
            args.add("-q");
            thread = new Thread(() -> {
                try {
                    exitCode = Gradle.run(Paths.mod(), log, args.toArray(String[]::new));
                } catch (RuntimeException e) {
                    System.err.println("[harness] " + e.getMessage());
                    exitCode = -1;
                }
            }, "harness-capture");
            thread.setDaemon(true);
        }

        public static Capture start(List<Nodes.Node> nodes, Path log) {
            List<Nodes.Node> fabric = nodes.stream().filter(Nodes.Node::fabric).toList();
            if (!fabric.isEmpty()) {
                // Loom refuses to remap a Golem Core jar older than its sources; build it first. `build`,
                // not `jar`: Loom's `jar` writes a dev jar under devlibs, and the guard (and the composite
                // build) look at the remapped jar under libs.
                System.out.println("[harness] building Golem Core for " + Nodes.namesOf(fabric));
                List<String> args = new ArrayList<>();
                for (Nodes.Node node : fabric) args.add(":" + node.name() + ":build");
                args.addAll(List.of("-x", "test", "-q"));
                int code = Gradle.run(Paths.golemCore(), log, args.toArray(String[]::new));
                if (code != 0) throw new HarnessException("Golem Core build failed for " + Nodes.namesOf(fabric) + "; see " + log);
            }
            System.out.println("[harness] building and capturing the client launch of " + Nodes.namesOf(nodes) + " (one Gradle run)");
            Capture capture = new Capture(nodes, log);
            capture.thread.start();
            return capture;
        }

        /** Blocks until this node's spec has been rewritten by this run; throws if the run ended without it. */
        public void await(Nodes.Node node) {
            while (!ready(node)) sleep(250);
        }

        /** True once this run has rewritten the node's spec; throws if the run ended without it. */
        public boolean ready(Nodes.Node node) {
            // Read the exit code before the file: a spec written just before Gradle exits is still seen.
            Integer code = exitCode;
            Path file = Paths.specFile(node);
            if (modified(file) != before.getOrDefault(node.name(), -1L)) return true;
            if (code != null) {
                throw new HarnessException(code != 0 ? "launch capture failed for " + node.name() + "; see " + log
                        : "gradle finished but wrote no spec at " + file);
            }
            return false;
        }

        /** Waits for the Gradle run itself to end. */
        public void join() {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private static long modified(Path file) {
            try {
                return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1L;
            } catch (IOException e) {
                return -1L;
            }
        }
    }

    /**
     * True when no build input of the node is newer than its captured spec, so Gradle would only
     * confirm that everything is up to date (half a minute or more of JVM start and configuration).
     * Inputs are both projects' {@code src}, {@code gradle} and top-level build files plus the node's
     * own {@code versions/<node>} folder (outputs, run directories and logs excluded). Stonecutter 0.9
     * generates each node into its own build directory, so building another node does not touch
     * {@code src} and does not defeat this check.
     */
    public static boolean specCurrent(Nodes.Node node) {
        Path spec = Paths.specFile(node);
        if (!Files.exists(spec)) return false;
        try {
            long specTime = Files.getLastModifiedTime(spec).toMillis();
            for (Path project : List.of(Paths.mod(), Paths.golemCore())) {
                if (newerThan(project.resolve("src"), specTime) || newerThan(project.resolve("gradle"), specTime)
                        || newerThan(project.resolve("versions").resolve(node.name()), specTime)) {
                    return false;
                }
                try (Stream<Path> top = Files.list(project)) {
                    for (Path file : top.toList()) {
                        String name = file.getFileName().toString();
                        if ((name.endsWith(".kts") || name.endsWith(".properties")) && Files.getLastModifiedTime(file).toMillis() > specTime) {
                            return false;
                        }
                    }
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static final Set<String> SKIPPED_DIRS = Set.of("build", "runs", "logs", ".gradle", "run");

    private static boolean newerThan(Path root, long time) throws IOException {
        if (!Files.exists(root)) return false;
        boolean[] found = {false};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && SKIPPED_DIRS.contains(dir.getFileName().toString())) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.lastModifiedTime().toMillis() > time) {
                    found[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return found[0];
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
        List<String> program = new ArrayList<>(spec.programArgs());
        if (options.username != null) {
            // Minecraft rejects a repeated --username, so the dev run's own one is dropped first.
            int at = program.indexOf("--username");
            if (at >= 0) program.subList(at, Math.min(at + 2, program.size())).clear();
            program.add("--username");
            program.add(options.username);
        }
        argfile.addAll(program);
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
                sleep(500);
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
                    sleep(500);
                    continue;
                }
                boolean overlay = !status.get("overlay").isJsonNull();
                boolean screen = !status.get("screen").isJsonNull();
                if (!overlay && (screen || status.get("in_world").getAsBoolean())) break;
                if (!process.isAlive()) throw new HarnessException(node.name() + " exited during startup");
                if (System.currentTimeMillis() > deadline) throw new HarnessException(node.name() + " never left the loading overlay");
                sleep(500);
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
