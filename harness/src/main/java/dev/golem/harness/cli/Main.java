package dev.golem.harness.cli;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.golem.harness.api.Client;
import dev.golem.harness.api.HarnessException;
import dev.golem.harness.api.Scenario;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code harness <command> [options]}: see the usage text. */
public final class Main {

    private static final String USAGE = """
            golem-harness — in-game visual test harness for Golem Core mods (two dev clients at a time by default, never focused)
            Reads harness.json from the working directory or its parents (or --config <file> / HARNESS_CONFIG).

            Lifecycle
              up      [--nodes a,b | --all] [--rebuild | --no-build] [--no-sodium] [--show] [--heap 2G] [--world harness] [--max-clients 2]
                      [--world-type flat|seeded]   seeded = default world preset, fixed seed, world 'harness_seeded'
                      [--username Name]   the offline player name (default: the loader's dev name, Dev / Player###)
                      [--server host:port] join a dedicated server (vanilla quick play) instead of the harness world;
                                          see "Multiplayer" in the README
                      [--size 1920x1080]  the window size (default 1280x720); check status window_width/height,
                                          an OS may shrink a window larger than the screen
                      Gradle runs once for all nodes and is skipped for a node whose build inputs are older than its
                      captured launch (--rebuild forces it); each client boots as soon as its launch is captured.
                      --all  start EVERY declared node at once (cap: allNodesMaxClients). Opt-in only: agents must
                             not use it, or raise --max-clients above 2, unless the user asked for more than two nodes.
              down    [--nodes a,b | --all]
              restart [--nodes a,b | --all] (down + up, same options as up)
              status
              logs    --node N [--lines 80] [--grep regex]
              spec    --node N            capture the launch spec only

            Driving one client (--node may be omitted when exactly one client is up)
              cmd        --node N <slash command...>
              key        --node N <key> [--hold ticks]          key.use, w, space, f3, mouse.left, key.hotbar.3
              type       --node N <text>
              click      --node N <guiX> <guiY> [--button 0]
              look       --node N <yaw> <pitch>
              lookat     --node N <x> <y> <z>
              use | attack --node N [--hold ticks]
              hotbar     --node N <1..9>
              tp         --node N <x> <y> <z>
              wait       --node N <ticks>
              query      --node N <kind> [k=v ...]             status player inventory block block_entity entities screen log
              screenshot --node N <name> [--crop x,y,w,h]      writes harness/out/adhoc/<node>/<name>.png
              exec       --node N '<json>'                     raw request, e.g. {"cmd":"wait_screen","contains":"Barrel"}

            Scenarios and worlds
              run   <Scenario.java> [--nodes a,b] [--label name] [--echo]
              world snapshot | reset | create --node N [--world harness] [--world-type flat|seeded]

              clean [--older-than <hours>]                delete runs/screenshots/logs under "out" older than that
                                                          (default retentionHours from harness.json, 10; 0 = everything).
                                                          up, restart and run do this automatically first.
              config                                      print the resolved harness.json

            Nodes come from harness.json.
            """;

    private Main() {}

    public static void main(String[] argv) {
        try {
            System.exit(run(argv));
        } catch (HarnessException e) {
            System.err.println("[harness] " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(3);
        }
    }

    private static int run(String[] argv) throws Exception {
        if (argv.length == 0 || argv[0].equals("help") || argv[0].equals("--help") || argv[0].equals("-h")) {
            System.out.print(USAGE);
            return 0;
        }
        Args args = Args.parse(argv);
        Config config = Config.load(args.get("config", null));
        if (args.command.equals("config")) {
            System.out.println("config: " + config.dir.resolve("harness.json"));
            System.out.println("mod: " + config.mod);
            System.out.println("golemCore: " + config.golemCore);
            System.out.println("scenarios: " + config.scenarios);
            System.out.println("fixtures: " + config.fixtures);
            System.out.println("out: " + config.out);
            System.out.println("world: " + config.world);
            System.out.println("nodes: " + Nodes.names());
            System.out.println("defaultNodes: " + config.defaultNodes);
            System.out.println("maxClients: " + config.maxClients);
            System.out.println("retentionHours: " + config.retentionHours);
            System.out.println("allNodesMaxClients: " + config.allNodesMaxClients + " (up --all)");
            return 0;
        }
        State state = State.load();
        state.prune();
        if (args.command.equals("clean")) {
            Cleanup.run(Double.parseDouble(args.get("older-than", Double.toString(config.retentionHours))), state);
            return 0;
        }
        if (config.retentionHours > 0 && List.of("up", "restart", "run").contains(args.command)) {
            Cleanup.run(config.retentionHours, state);
        }
        switch (args.command) {
            case "up": return up(args, state);
            case "down": return down(args, state);
            case "restart": {
                down(args, state);
                return up(args, state);
            }
            case "status": return status(state);
            case "logs": return logs(args, state);
            case "spec": {
                Nodes.Node node = Nodes.get(args.require("node"));
                Path log = Paths.out().resolve("gradle").resolve(node.name() + "-" + Launcher.stamp() + ".log");
                LaunchSpec spec = Launcher.spec(node, true, log);
                System.out.println(spec.mainClass() + " with " + spec.classpath().size() + " classpath entries; spec at " + Paths.specFile(node));
                return 0;
            }
            case "run": return runScenario(args, state);
            case "world": return world(args, state);
            case "cmd": case "key": case "type": case "click": case "look": case "lookat": case "use": case "attack":
            case "hotbar": case "tp": case "wait": case "query": case "screenshot": case "exec":
                return drive(args, state);
            default:
                System.err.println("unknown command " + args.command + "\n" + USAGE);
                return 1;
        }
    }

    // ---- lifecycle -------------------------------------------------------------------------

    private static Launcher.Options options(Args args) {
        Launcher.Options o = new Launcher.Options();
        o.build = args.flag("no-build") ? "never" : args.flag("rebuild") ? "always" : "auto";
        o.sodium = !args.flag("no-sodium");
        o.show = args.flag("show");
        o.heap = args.get("heap", o.heap);
        o.worldType = args.get("world-type", "flat");
        if (!o.worldType.equals("flat") && !o.worldType.equals("seeded")) throw new HarnessException("--world-type flat | seeded");
        o.world = args.get("world", o.worldType.equals("seeded") ? Client.SEEDED_WORLD : o.world);
        o.username = args.get("username", null);
        o.server = args.get("server", null);
        o.verbose = args.flag("verbose");
        o.guiScale = Integer.parseInt(args.get("gui-scale", "2"));
        String size = args.get("size", null);
        if (size != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{3,5})x(\\d{3,5})").matcher(size);
            if (!m.matches()) throw new HarnessException("--size WIDTHxHEIGHT, e.g. 1920x1080");
            o.width = Integer.parseInt(m.group(1));
            o.height = Integer.parseInt(m.group(2));
            warnIfLargerThanScreen(o.width, o.height);
        }
        return o;
    }

    /**
     * Windows shrinks a window larger than the desktop to fit it (2560x1440 on a 1920x1200 screen became a 1924x1181
     * framebuffer, 2026-10-07), and does it after boot, so {@code status} still shows the requested size at first.
     */
    private static void warnIfLargerThanScreen(int width, int height) {
        try {
            java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
            if (width > screen.width || height > screen.height) {
                System.err.println("[harness] WARNING: --size " + width + "x" + height + " is larger than the screen ("
                        + screen.width + "x" + screen.height + "); the OS will shrink the window. Read the real size"
                        + " from the client's framebuffer, not from the requested one.");
            }
        } catch (Throwable headless) {
            // no display information: nothing to compare against
        }
    }

    /**
     * Starts the requested clients. One Gradle run captures every launch that needs rebuilding (none
     * when the inputs are unchanged); it runs in the background and each client is launched the moment
     * its own spec lands, so a client boots while Gradle still builds the next node. All clients then
     * reach their world in parallel.
     */
    private static int up(Args args, State state) throws IOException {
        // `--all` is the explicit opt-in to run every declared node at once. Without it the ordinary
        // maxClients cap (2) applies, so `--nodes all` alone still refuses past the second client.
        boolean all = args.flag("all");
        if (all && args.has("nodes")) throw new HarnessException("--all and --nodes are exclusive");
        List<Nodes.Node> requested = all ? new ArrayList<>(Nodes.all())
                : args.has("nodes") ? Nodes.parse(args.get("nodes", "")) : Nodes.parse(String.join(",", Nodes.defaultUp()));
        int cap = all ? Config.get().allNodesMaxClients : Config.get().maxClients;
        int max = Integer.parseInt(args.get("max-clients", Integer.toString(cap)));
        Launcher.Options options = options(args);
        if (max > Config.get().maxClients) {
            System.out.println("[harness] running up to " + max + " clients at once (" + options.heap + " heap each; ~2 GB working set per client)."
                    + " Agents: only do this when the user asked for more than two nodes.");
        }
        List<Nodes.Node> nodes = new ArrayList<>();
        for (Nodes.Node node : requested) {
            if (state.get(node.name()).map(State.Live::alive).orElse(false)) {
                System.out.println("[harness] " + node.name() + ": already up on port " + node.port());
            } else if (!nodes.contains(node)) {
                nodes.add(node);
            }
        }
        if (nodes.isEmpty()) return 0;
        long live = state.all().values().stream().filter(State.Live::alive).count();
        if (live + nodes.size() > max) {
            throw new HarnessException("refusing to start " + Nodes.namesOf(nodes) + ": " + live + " client(s) already up and --max-clients is "
                    + max + ". Run `harness down` first"
                    + (all ? "." : ", or use `up --all` to run every node at once (only when more than two nodes were asked for)."));
        }

        List<Nodes.Node> build = new ArrayList<>();
        for (Nodes.Node node : nodes) {
            boolean needed = switch (options.build) {
                case "always" -> true;
                case "never" -> !Files.exists(Paths.specFile(node));
                default -> !Launcher.specCurrent(node);
            };
            if (needed) build.add(node);
            else System.out.println("[harness] " + node.name() + ": build inputs unchanged since the last capture, skipping Gradle"
                    + (options.build.equals("never") ? "" : " (--rebuild forces it)"));
        }
        Path gradleLog = Paths.out().resolve("gradle").resolve(String.join("+", Nodes.namesOf(build)) + "-" + Launcher.stamp() + ".log");
        Launcher.Capture capture = null;
        if (!build.isEmpty()) {
            try {
                capture = Launcher.Capture.start(build, gradleLog);
            } catch (HarnessException e) {
                System.err.println("[harness] " + e.getMessage());
                return 1;
            }
        }

        int failures = 0;
        List<Thread> waiters = new ArrayList<>();
        int[] waitFailures = {0};
        // Launch in whichever order Gradle finishes the captures, not the order the nodes were named.
        List<Nodes.Node> pending = new ArrayList<>(nodes);
        while (!pending.isEmpty()) {
            Nodes.Node node = null;
            for (Nodes.Node candidate : pending) {
                try {
                    if (capture == null || !build.contains(candidate) || capture.ready(candidate)) {
                        node = candidate;
                        break;
                    }
                } catch (HarnessException e) {
                    System.err.println("[harness] " + candidate.name() + ": " + e.getMessage());
                    pending.remove(candidate);
                    failures++;
                    break;
                }
            }
            if (node == null) {
                if (!pending.isEmpty()) Launcher.sleep(250);
                continue;
            }
            pending.remove(node);
            launchOne(node, options, state, waiters, waitFailures);
        }
        for (Thread waiter : waiters) {
            try {
                waiter.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (capture != null) capture.join();
        synchronized (waitFailures) {
            failures += waitFailures[0];
        }
        return failures == 0 ? 0 : 1;
    }

    /** Launches one client from its captured spec and starts a thread that waits for it to reach its world. */
    private static void launchOne(Nodes.Node node, Launcher.Options options, State state, List<Thread> waiters, int[] waitFailures) {
        try {
            LaunchSpec spec = LaunchSpec.read(Paths.specFile(node));
            Launcher.prepareRunDir(node, options);
            boolean worldExists = options.server != null || World.exists(node, options.world);
            Path clientLog = Paths.out().resolve("clients").resolve(node.name() + "-" + Launcher.stamp() + ".log");
            Files.createDirectories(clientLog.getParent());
            System.out.println("[harness] " + node.name() + ": launching (heap " + options.heap + ", port " + node.port()
                    + (options.server != null ? ", joining server " + options.server : worldExists ? ", quick play '" + options.world + "'" : ", no world yet") + ")");
            Process process = Launcher.launch(node, spec, options, clientLog, worldExists);
            state.put(new State.Live(node.name(), process.pid(), node.port(), Paths.runDir(node).toString(),
                    clientLog.toString(), options.world, System.currentTimeMillis()));
            Thread waiter = new Thread(() -> {
                if (!awaitClient(node, process, options, worldExists, clientLog, state)) {
                    synchronized (waitFailures) {
                        waitFailures[0]++;
                    }
                }
            }, "harness-up-" + node.name());
            waiter.start();
            waiters.add(waiter);
        } catch (HarnessException | IOException e) {
            System.err.println("[harness] " + node.name() + ": " + e.getMessage());
            synchronized (waitFailures) {
                waitFailures[0]++;
            }
        }
    }

    /** Waits for one launched client to reach its world (taking the first snapshot if it made the world). */
    private static boolean awaitClient(Nodes.Node node, Process process, Launcher.Options options, boolean worldExists, Path clientLog, State state) {
        try {
            Launcher.waitReady(node, process, options, worldExists);
        } catch (HarnessException e) {
            System.err.println("[harness] " + node.name() + ": " + e.getMessage() + "\n" + Gradle.tail(clientLog, 40));
            if (process.isAlive()) process.destroyForcibly();
            state.remove(node.name());
            return false;
        }
        if (!worldExists) {
            // First boot created the world; the client must leave it for a clean copy of the save.
            try (Client client = Client.connect(node.name(), node.port())) {
                client.leaveWorld(60_000L);
                World.snapshot(node, options.world);
                client.joinWorld(options.world, 180_000L);
            } catch (HarnessException | IOException e) {
                System.err.println("[harness] " + node.name() + ": first world snapshot failed: " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    private static int down(Args args, State state) {
        List<Nodes.Node> nodes;
        if (args.has("nodes")) nodes = Nodes.parse(args.get("nodes", ""));
        else nodes = state.all().keySet().stream().map(Nodes::get).toList();
        for (Nodes.Node node : nodes) {
            State.Live live = state.get(node.name()).orElse(null);
            if (live == null) continue;
            if (live.alive()) {
                try (Client client = Client.connect(node.name(), node.port())) {
                    client.stop();
                } catch (RuntimeException ignored) {
                    // fall through to the hard kill
                }
                ProcessHandle.of(live.pid()).ifPresent(handle -> {
                    long deadline = System.currentTimeMillis() + 20_000L;
                    while (handle.isAlive() && System.currentTimeMillis() < deadline) Launcher.sleep(500);
                    if (handle.isAlive()) {
                        handle.descendants().forEach(ProcessHandle::destroyForcibly);
                        handle.destroyForcibly();
                    }
                });
                System.out.println("[harness] " + node.name() + ": stopped");
            }
            state.remove(node.name());
        }
        return 0;
    }

    private static int status(State state) {
        if (state.all().isEmpty()) {
            System.out.println("no harness clients running");
            return 0;
        }
        for (State.Live live : state.all().values()) {
            StringBuilder line = new StringBuilder(live.node()).append("  pid ").append(live.pid()).append("  port ").append(live.port());
            try (Client client = Client.connect(live.node(), live.port())) {
                JsonObject s = client.status();
                line.append("  in_world=").append(s.get("in_world")).append("  screen=").append(s.get("screen"))
                        .append("  fps=").append(s.get("fps")).append("  heap=").append(s.get("heap_used_mb")).append('/')
                        .append(s.get("heap_max_mb")).append("MB  focused=").append(s.get("focused"));
                if (s.has("player")) {
                    JsonObject p = s.getAsJsonObject("player");
                    line.append(String.format("  at %.1f %.1f %.1f", p.get("x").getAsDouble(), p.get("y").getAsDouble(), p.get("z").getAsDouble()));
                }
            } catch (RuntimeException e) {
                line.append("  (listener not answering: ").append(e.getMessage()).append(')');
            }
            System.out.println(line);
        }
        return 0;
    }

    private static int logs(Args args, State state) throws IOException {
        Nodes.Node node = Nodes.get(args.require("node"));
        State.Live live = state.get(node.name()).orElseThrow(() -> new HarnessException(node.name() + " is not up"));
        int lines = Integer.parseInt(args.get("lines", "80"));
        String grep = args.get("grep", null);
        List<String> all = Files.readAllLines(Path.of(live.log()));
        List<String> picked = new ArrayList<>();
        for (String line : all) if (grep == null || line.matches(".*(" + grep + ").*")) picked.add(line);
        for (String line : picked.subList(Math.max(0, picked.size() - lines), picked.size())) System.out.println(line);
        return 0;
    }

    // ---- driving ---------------------------------------------------------------------------

    private static Nodes.Node oneNode(Args args, State state) {
        if (args.has("node")) return Nodes.get(args.get("node", ""));
        List<String> live = state.all().values().stream().filter(State.Live::alive).map(State.Live::node).toList();
        if (live.size() == 1) return Nodes.get(live.get(0));
        throw new HarnessException("--node required (" + (live.isEmpty() ? "no client up" : "up: " + live) + ")");
    }

    private static int drive(Args args, State state) {
        Nodes.Node node = oneNode(args, state);
        if (!state.get(node.name()).map(State.Live::alive).orElse(false)) throw new HarnessException(node.name() + " is not up");
        try (Client client = Client.connect(node.name(), node.port())) {
            client.setScreenshotDir(Paths.out().resolve("adhoc").resolve(node.name()));
            List<String> p = args.positional;
            JsonObject out;
            switch (args.command) {
                case "cmd": client.cmd(String.join(" ", p)); out = null; break;
                case "key": client.key(p.get(0), Integer.parseInt(args.get("hold", "1"))); out = null; break;
                case "type": client.type(String.join(" ", p)); out = null; break;
                case "click": client.click(Double.parseDouble(p.get(0)), Double.parseDouble(p.get(1)), Integer.parseInt(args.get("button", "0"))); out = null; break;
                case "look": client.look(Double.parseDouble(p.get(0)), Double.parseDouble(p.get(1))); out = null; break;
                case "lookat": client.lookAt(Integer.parseInt(p.get(0)), Integer.parseInt(p.get(1)), Integer.parseInt(p.get(2))); out = null; break;
                case "use": client.use(Integer.parseInt(args.get("hold", "1"))); out = null; break;
                case "attack": client.attack(Integer.parseInt(args.get("hold", "1"))); out = null; break;
                case "hotbar": client.hotbar(Integer.parseInt(p.get(0))); out = null; break;
                case "tp": client.teleport(Double.parseDouble(p.get(0)), Double.parseDouble(p.get(1)), Double.parseDouble(p.get(2))); out = null; break;
                case "wait": client.waitTicks(Integer.parseInt(p.get(0))); out = null; break;
                case "query": {
                    JsonObject params = new JsonObject();
                    for (String kv : p.subList(1, p.size())) {
                        int eq = kv.indexOf('=');
                        if (eq < 0) continue;
                        String k = kv.substring(0, eq);
                        String v = kv.substring(eq + 1);
                        try {
                            params.addProperty(k, Double.parseDouble(v));
                            if (v.matches("-?\\d+")) params.addProperty(k, Integer.parseInt(v));
                        } catch (NumberFormatException e) {
                            if (v.equals("true") || v.equals("false")) params.addProperty(k, Boolean.parseBoolean(v));
                            else params.addProperty(k, v);
                        }
                    }
                    out = client.query(p.get(0), params);
                    break;
                }
                case "screenshot": {
                    int[] crop = null;
                    if (args.has("crop")) {
                        String[] parts = args.get("crop", "").split(",");
                        crop = new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])};
                    }
                    Path path = client.screenshot(p.isEmpty() ? "shot-" + Launcher.stamp() : p.get(0), crop);
                    System.out.println(path);
                    return 0;
                }
                case "exec": out = client.raw(String.join(" ", p)); break;
                default: throw new HarnessException("unhandled " + args.command);
            }
            if (out != null) System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(out));
            else System.out.println("ok");
            return 0;
        }
    }

    // ---- scenarios and worlds --------------------------------------------------------------

    private static int runScenario(Args args, State state) throws IOException {
        if (args.positional.isEmpty()) throw new HarnessException("run: scenario file required");
        Path source = Path.of(args.positional.get(0));
        if (!Files.exists(source)) source = Paths.scenarios().resolve(args.positional.get(0));
        if (!Files.exists(source)) throw new HarnessException("no such scenario: " + args.positional.get(0));
        List<Nodes.Node> nodes = args.has("nodes") ? Nodes.parse(args.get("nodes", ""))
                : state.all().values().stream().filter(State.Live::alive).map(l -> Nodes.get(l.node())).toList();
        if (nodes.isEmpty()) throw new HarnessException("no live clients; run `harness up` first");
        String label = args.get("label", source.getFileName().toString().replaceFirst("\\.java$", ""));
        Path outDir = Paths.out().resolve("runs").resolve(Launcher.stamp() + "-" + label);
        Scenario scenario = Runner.load(source);
        System.out.println("[harness] running " + source.getFileName() + " on " + Nodes.namesOf(nodes) + " -> " + outDir);
        List<Runner.Result> results = Runner.run(scenario, nodes, state, outDir, args.flag("echo"));
        Report.write(outDir, source.getFileName().toString(), results);
        boolean allPassed = true;
        for (Runner.Result r : results) {
            System.out.println("  " + (r.passed() ? "PASS" : "FAIL") + "  " + r.node() + "  " + (r.millis() / 1000.0) + " s  " + r.message());
            allPassed &= r.passed();
        }
        System.out.println("[harness] report: " + outDir.resolve("report.md"));
        return allPassed ? 0 : 1;
    }

    private static int world(Args args, State state) throws IOException {
        if (args.positional.isEmpty()) throw new HarnessException("world: snapshot | reset | create");
        String action = args.positional.get(0);
        Nodes.Node node = oneNode(args, state);
        String world = args.get("world", state.get(node.name()).map(State.Live::world).orElse("harness"));
        boolean live = state.get(node.name()).map(State.Live::alive).orElse(false);
        switch (action) {
            case "snapshot": {
                if (live) {
                    try (Client client = Client.connect(node.name(), node.port())) {
                        client.leaveWorld(60_000L);
                        World.snapshot(node, world);
                        client.joinWorld(world, 180_000L);
                    }
                } else {
                    World.snapshot(node, world);
                }
                return 0;
            }
            case "reset": {
                if (live) {
                    try (Client client = Client.connect(node.name(), node.port())) {
                        World.reset(node, client, world);
                    }
                } else {
                    World.restore(node, world);
                    System.out.println("[harness] " + node.name() + ": save restored (client not running)");
                }
                return 0;
            }
            case "create": {
                if (!live) throw new HarnessException(node.name() + " must be up to create a world");
                try (Client client = Client.connect(node.name(), node.port())) {
                    if (client.status().get("in_world").getAsBoolean()) client.leaveWorld(60_000L);
                    String type = args.get("world-type", "flat");
                    client.createWorld(world, type, Client.SEEDED_WORLD_SEED, 420_000L);
                    if (type.equals("seeded")) client.freezeWorld();
                    client.leaveWorld(60_000L);
                    World.snapshot(node, world);
                    client.joinWorld(world, 180_000L);
                }
                return 0;
            }
            default:
                throw new HarnessException("world: snapshot | reset | create");
        }
    }

    /** Tiny argument parser: {@code --key value}, {@code --flag}, positionals. */
    static final class Args {
        final String command;
        final Map<String, String> options = new LinkedHashMap<>();
        final List<String> positional = new ArrayList<>();
        private static final List<String> FLAGS = List.of("no-build", "rebuild", "no-sodium", "show", "all", "echo", "verbose");

        private Args(String command) {
            this.command = command;
        }

        static Args parse(String[] argv) {
            Args args = new Args(argv[0]);
            for (int i = 1; i < argv.length; i++) {
                String a = argv[i];
                if (a.startsWith("--")) {
                    String key = a.substring(2);
                    int eq = key.indexOf('=');
                    if (eq >= 0) {
                        args.options.put(key.substring(0, eq), key.substring(eq + 1));
                    } else if (FLAGS.contains(key) || i + 1 >= argv.length || argv[i + 1].startsWith("--")) {
                        args.options.put(key, "true");
                    } else {
                        args.options.put(key, argv[++i]);
                    }
                } else {
                    args.positional.add(a);
                }
            }
            return args;
        }

        boolean has(String key) {
            return options.containsKey(key);
        }

        boolean flag(String key) {
            return "true".equals(options.get(key));
        }

        String get(String key, String fallback) {
            return options.getOrDefault(key, fallback);
        }

        String require(String key) {
            String v = options.get(key);
            if (v == null) throw new HarnessException("--" + key + " is required");
            return v;
        }
    }
}
