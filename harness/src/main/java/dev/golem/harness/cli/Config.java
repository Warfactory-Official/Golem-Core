package dev.golem.harness.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.golem.harness.api.HarnessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code harness.json}: the one file a mod repository provides to use the shared harness.
 *
 * <pre>{@code
 * {
 *   "name": "mymod",                       // label for the CLI and the world name
 *   "mod": ".",                            // Stonecutter mod project (has gradlew, versions/<node>)
 *   "golemCore": "../Golem-Core",          // Golem Core checkout used by the composite build; null without one
 *   "scenarios": "harness/scenarios",      // where `run` looks for bare scenario names
 *   "fixtures": "harness/fixtures",        // .nbt templates published into the harness world
 *   "out": "harness/out",                  // screenshots, logs, reports
 *   "optionsTemplate": null,               // optional options.txt override (${guiScale} placeholder)
 *   "world": "harness",
 *   "defaultNodes": ["1.21.1-neoforge", "1.21.1-fabric"],
 *   "maxClients": 2,                       // cap for an ordinary `up` (keep it 2)
 *   "allNodesMaxClients": 6,               // cap for `up --all` (default: every declared node)
 *   "retentionHours": 10,                  // older runs/screenshots/logs under "out" are deleted; 0 = keep all
 *   "nodes": [
 *     {"name": "26.1-neoforge", "minecraft": "26.1", "loader": "neoforge", "port": 47601, "packFormat": 84, "java": 21}
 *   ]
 * }
 * }</pre>
 *
 * <p>All paths are relative to the directory holding the file. The file is found through
 * {@code HARNESS_CONFIG}, {@code --config}, or by walking up from the working directory.
 */
public final class Config {

    public final Path dir;
    public final String name;
    public final Path mod;
    /** Null when the mod builds against released Golem Core jars instead of a checkout. */
    public final Path golemCore;
    public final Path scenarios;
    public final Path fixtures;
    public final Path out;
    public final Path optionsTemplate;
    public final String world;
    public final List<String> defaultNodes;
    public final int maxClients;
    /** Run folders, ad-hoc screenshots and logs older than this are deleted at the next run; 0 keeps everything. */
    public final double retentionHours;
    /** Cap for {@code up --all}, the explicit opt-in to run every declared node at once. */
    public final int allNodesMaxClients;
    public final List<Nodes.Node> nodes;

    private static Config current;

    private Config(Path file, JsonObject json) {
        dir = file.toAbsolutePath().getParent();
        name = str(json, "name", "mod");
        mod = dir.resolve(str(json, "mod", ".")).normalize();
        golemCore = json.has("golemCore") && json.get("golemCore").isJsonNull() ? null
                : dir.resolve(str(json, "golemCore", "../Golem-Core")).normalize();
        scenarios = dir.resolve(str(json, "scenarios", "harness/scenarios")).normalize();
        fixtures = dir.resolve(str(json, "fixtures", "harness/fixtures")).normalize();
        out = dir.resolve(str(json, "out", "harness/out")).normalize();
        String template = str(json, "optionsTemplate", null);
        optionsTemplate = template == null ? null : dir.resolve(template).normalize();
        world = str(json, "world", "harness");
        maxClients = json.has("maxClients") ? json.get("maxClients").getAsInt() : 2;
        retentionHours = json.has("retentionHours") ? json.get("retentionHours").getAsDouble() : 10;
        List<Nodes.Node> list = new ArrayList<>();
        JsonArray array = json.has("nodes") ? json.getAsJsonArray("nodes") : new JsonArray();
        int i = 0;
        for (JsonElement e : array) {
            JsonObject n = e.getAsJsonObject();
            String nodeName = n.get("name").getAsString();
            String mc = str(n, "minecraft", nodeName.substring(0, nodeName.indexOf('-')));
            String loader = str(n, "loader", nodeName.substring(nodeName.indexOf('-') + 1));
            int port = n.has("port") ? n.get("port").getAsInt() : 47601 + i;
            int pack = n.has("packFormat") ? n.get("packFormat").getAsInt() : 0;
            int java = n.has("java") ? n.get("java").getAsInt() : 21;
            list.add(new Nodes.Node(nodeName, mc, loader, port, pack, java));
            i++;
        }
        if (list.isEmpty()) throw new HarnessException(file + " declares no nodes");
        nodes = List.copyOf(list);
        allNodesMaxClients = json.has("allNodesMaxClients") ? json.get("allNodesMaxClients").getAsInt() : nodes.size();
        List<String> defaults = new ArrayList<>();
        if (json.has("defaultNodes")) json.getAsJsonArray("defaultNodes").forEach(el -> defaults.add(el.getAsString()));
        if (defaults.isEmpty()) defaults.add(nodes.get(0).name());
        defaultNodes = List.copyOf(defaults);
    }

    private static String str(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    /** The active config; loaded on first use. */
    public static synchronized Config get() {
        if (current == null) current = load(null);
        return current;
    }

    public static synchronized Config load(String explicit) {
        Path file = find(explicit);
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            current = new Config(file, json);
            return current;
        } catch (IOException | RuntimeException e) {
            throw new HarnessException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    private static Path find(String explicit) {
        if (explicit != null && !explicit.isBlank()) return Path.of(explicit).toAbsolutePath();
        String env = System.getenv("HARNESS_CONFIG");
        if (env != null && !env.isBlank()) return Path.of(env).toAbsolutePath();
        Path cwd = Path.of("").toAbsolutePath();
        for (Path p = cwd; p != null; p = p.getParent()) {
            Path candidate = p.resolve("harness.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new HarnessException("no harness.json found in " + cwd + " or its parents; pass --config <file> or set HARNESS_CONFIG");
    }
}
