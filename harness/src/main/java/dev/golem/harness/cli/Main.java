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
            golem-harness — in-game visual test harness for Golem Core mods (two dev clients at a time, never focused)
            Reads harness.json from the working directory or its parents (or --config <file> / HARNESS_CONFIG).

            Lifecycle
              up      [--nodes a,b] [--no-build] [--no-sodium] [--show] [--heap 2G] [--world harness] [--max-clients 2]
                      [--world-type flat|seeded]   seeded = default world preset, fixed seed, world 'harness_seeded'
              down    [--nodes a,b | --all]
              restart [--nodes a,b] (down + up, same options as up)
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
            return 0;
        }
        State state = State.load();
        state.prune();
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
        o.build = !args.flag("no-build");
        o.sodium = !args.flag("no-sodium");
        o.show = args.flag("show");
        o.heap = args.get("heap", o.heap);
        o.worldType = args.get("world-type", "flat");
        if (!o.worldType.equals("flat") && !o.worldType.equals("seeded")) throw new HarnessException("--world-type flat | seeded");
        o.world = args.get("world", o.worldType.equals("seeded") ? Client.SEEDED_WORLD : o.world);
        o.verbose = args.flag("verbose");
        o.guiScale = Integer.parseInt(args.get("gui-scale", "2"));
        return o;
    }

    private static int up(Args args, State state) throws IOException {
        List<Nodes.Node> nodes = args.has("nodes") ? Nodes.parse(args.get("nodes", "")) : Nodes.parse(String.join(",", Nodes.defaultUp()));
        int max = Integer.parseInt(args.get("max-clients", Integer.toString(Config.get().maxClients)));
        Launcher.Options options = options(args);
        int failures = 0;
        for (Nodes.Node node : nodes) {
            if (state.get(node.name()).map(State.Live::alive).orElse(false)) {
                System.out.println("[harness] " + node.name() + ": already up on port " + node.port());
                continue;
            }
            long live = state.all().values().stream().filter(State.Live::alive).count();
            if (live >= max) {
                throw new HarnessException("refusing to start " + node.name() + ": " + live + " clients already up (--max-clients " + max + "). Run `harness down` first.");
            }
            Path log = Paths.out().resolve("gradle").resolve(node.name() + "-" + Launcher.stamp() + ".log");
            try {
                LaunchSpec spec = Launcher.spec(node, options.build, log);
                Launcher.prepareRunDir(node, options);
                boolean worldExists = World.exists(node, options.world);
                Path clientLog = Paths.out().resolve("clients").resolve(node.name() + "-" + Launcher.stamp() + ".log");
                Files.createDirectories(clientLog.getParent());
                System.out.println("[harness] " + node.name() + ": launching (heap " + options.heap + ", port " + node.port()
                        + (worldExists ? ", quick play '" + options.world + "'" : ", no world yet") + ")");
                Process process = Launcher.launch(node, spec, options, clientLog, worldExists);
                state.put(new State.Live(node.name(), process.pid(), node.port(), Paths.runDir(node).toString(),
                        clientLog.toString(), options.world, System.currentTimeMillis()));
                try {
                    Launcher.waitReady(node, process, options, worldExists);
                } catch (HarnessException e) {
                    System.err.println("[harness] " + node.name() + ": " + e.getMessage());
                    System.err.println(Gradle.tail(clientLog, 40));
                    if (process.isAlive()) process.destroyForcibly();
                    state.remove(node.name());
                    failures++;
                    continue;
                }
                if (!worldExists) {
                    // First boot created the world; the client must leave it for a clean copy of the save.
                    try (Client client = Client.connect(node.name(), node.port())) {
                        client.leaveWorld(60_000L);
                        World.snapshot(node, options.world);
                        client.joinWorld(options.world, 180_000L);
                    }
                }
            } catch (HarnessException e) {
                System.err.println("[harness] " + node.name() + ": " + e.getMessage());
                failures++;
            }
        }
        return failures == 0 ? 0 : 1;
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
        private static final List<String> FLAGS = List.of("no-build", "no-sodium", "show", "all", "echo", "verbose");

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
