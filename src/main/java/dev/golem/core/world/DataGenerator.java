package dev.golem.core.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.golem.core.GolemCore;
import dev.golem.core.platform.Lifecycle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;

/**
 * Writes {@link DataSource} rows ({@link RecipeSource} tables, fluid properties, data maps, ...) as datapack files: a
 * build tool, inert in a normal game. A mod's build starts a
 * headless server with {@code -Dgolem.data.output=<root>} and {@code -Dgolem.data.sources=<DataSources classes,
 * comma-separated>}; once the server has started (a row may hold item stacks, which 26.1 can build only after a server
 * loaded its registries, never in a data run) every row is written and the server is halted before anything else runs.
 *
 * <p>Output: {@code <root>/pack/data/<namespace>/<folder>/<id>.json} ({@code recipe/<table>} for a table: {@code "type"}
 * plus the table codec's JSON), and {@code recipe_removals/<table>.json} for a table source's {@link RecipeSource#remove}s.
 * Several sources may fill one folder; two rows of one folder and namespace may not share an id.
 * Each row is decoded and re-encoded first, so a row the schema cannot state fails instead of losing a field. The pack is
 * rewritten from scratch every run. {@value #MANIFEST} ({@code <namespace>/<folder> <files>} per line) is written last and
 * only when every table succeeded, so its presence means the run finished; otherwise the other tables are still written
 * and {@value #ERRORS} names the failed ones. Golem Core's {@code gradle/mod/generated-data.gradle.kts} packs the output on
 * every node and checks it against the manifest.
 */
public final class DataGenerator {

    public static final String OUTPUT = "golem.data.output";
    public static final String SOURCES = "golem.data.sources";
    public static final String MANIFEST = "manifest.txt";
    public static final String ERRORS = "errors.txt";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private DataGenerator() {}

    /** Arms the generator when this run was started to generate; a property read otherwise. */
    static void install() {
        String output = System.getProperty(OUTPUT);
        if (output == null || output.isBlank()) return;
        Lifecycle.onServerStarted(server -> run(server, Path.of(output)));
    }

    /** The server a run generates on, for {@link #loaded}. */
    private static volatile MinecraftServer running;

    private static void run(MinecraftServer server, Path root) {
        running = server;
        try {
            List<DataSources> owners = new ArrayList<>();
            for (String name : System.getProperty(SOURCES, "").split(",")) {
                if (!name.isBlank()) owners.add((DataSources) Class.forName(name.trim()).getDeclaredConstructor().newInstance());
            }
            int rows = write(root, owners, server.registryAccess());
            GolemCore.LOG.info("[DATA] generated {} row(s) from {} into {}; stopping", rows, owners.size(), root);
        } catch (Throwable e) {
            // no manifest is written, which fails the build with a pointer to this log
            GolemCore.LOG.error("[DATA] generation failed", e);
        }
        server.halt(false);
    }

    /** {@link RecipeSource#loaded}: the row's datapack file on the running server, decoded with the table's codec. */
    static <R> R loaded(RecipeTables.Table<R> table, String recipeId) {
        MinecraftServer server = running;
        if (server == null) throw new IllegalStateException("rows can be looked up only while the recipe generator runs");
        int colon = recipeId.indexOf(':');
        if (colon < 0) throw new IllegalArgumentException("not a recipe id: " + recipeId);
        String namespace = recipeId.substring(0, colon);
        String path = recipeId.substring(colon + 1);
        for (String folder : new String[] {"recipe", "recipes"}) { // the folder was plural before 1.21
            var resource = server.getResourceManager().getResource(dev.golem.core.Ids.of(namespace, folder + "/" + path + ".json"));
            if (resource.isEmpty()) continue;
            try (java.io.Reader reader = resource.get().openAsReader()) {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                String type = json.has("type") ? json.get("type").getAsString() : "";
                if (!table.id().equals(type)) throw new IllegalArgumentException(recipeId + " is a " + type + " row, not " + table.id());
                json.remove("type");
                return table.fromJson(json, server.registryAccess());
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        throw new IllegalArgumentException("no " + table.id() + " row " + recipeId + " in the loaded datapacks");
    }

    static int write(Path root, List<DataSources> owners, HolderLookup.Provider registries) throws IOException {
        Path pack = root.resolve("pack");
        Files.deleteIfExists(root.resolve(MANIFEST));
        Files.deleteIfExists(root.resolve(ERRORS));
        deleteTree(pack);
        Map<String, Integer> manifest = new TreeMap<>();
        Map<String, String> errors = new TreeMap<>();
        int total = 0;
        for (DataSources owner : owners) {
            Path data = pack.resolve("data").resolve(owner.namespace());
            for (DataSource<?> source : owner.sources()) {
                String key = owner.namespace() + "/" + source.folder();
                // one broken source must not hide the others: write the rest, then fail without a manifest
                try {
                    int rows = write(source, registries, data.resolve(source.folder()));
                    manifest.merge(key, rows, Integer::sum);
                    total += rows;
                    if (source instanceof RecipeSource<?> table && !table.removals().isEmpty()) {
                        String name = source.folder().substring("recipe/".length());
                        writeFile(data.resolve(RecipeTables.REMOVALS).resolve(name + ".json"),
                                REMOVALS.encode(List.copyOf(table.removals()), registries), source.describe() + " removals");
                        manifest.merge(owner.namespace() + "/" + RecipeTables.REMOVALS, 1, Integer::sum);
                        total++;
                    }
                } catch (RuntimeException e) {
                    GolemCore.LOG.error("[DATA] {} failed", key, e);
                    errors.merge(key, String.valueOf(e.getMessage()), (x, y) -> x + "; " + y);
                }
            }
        }
        if (!errors.isEmpty()) {
            Files.writeString(root.resolve(ERRORS), lines(errors, ": "), StandardCharsets.UTF_8);
            throw new IllegalStateException(errors.size() + " source(s) failed: " + errors.keySet() + " (see " + ERRORS + ")");
        }
        Files.writeString(root.resolve(MANIFEST), lines(manifest, " "), StandardCharsets.UTF_8);
        return total;
    }

    /** Encodes {@code recipe_removals} lists for {@link RecipeSource#remove}. */
    private static final RemovalSource REMOVALS = new RemovalSource() {
        @Override
        protected void registerDefaults() {}
    };

    private static <R> int write(DataSource<R> source, HolderLookup.Provider registries, Path folder) throws IOException {
        Map<String, R> rows = source.rows();
        Files.createDirectories(folder);
        for (Map.Entry<String, R> row : rows.entrySet()) {
            String where = source.describe() + " row " + row.getKey();
            JsonElement file;
            JsonElement again;
            try {
                file = source.encode(row.getValue(), registries);
                again = source.encode(source.decode(file, registries), registries);
            } catch (RuntimeException e) {
                throw new IllegalStateException(where + ": " + e.getMessage(), e);
            }
            if (!file.equals(again)) {
                throw new IllegalStateException(where + " does not survive its own codec:\n  wrote " + file + "\n  read  " + again);
            }
            writeFile(folder.resolve(row.getKey() + ".json"), file, where);
        }
        return rows.size();
    }

    private static void writeFile(Path path, JsonElement file, String where) throws IOException {
        // several sources may fill one folder (an addon's own rows and its changes to its base mod's), never one id
        if (Files.exists(path)) throw new IllegalStateException(where + ": another source already wrote " + path.getFileName());
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(file) + "\n", StandardCharsets.UTF_8);
    }

    private static String lines(Map<String, ?> entries, String separator) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, ?> entry : entries.entrySet()) {
            text.append(entry.getKey()).append(separator).append(entry.getValue()).append('\n');
        }
        return text.toString();
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path each : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(each);
        }
    }
}
