package dev.golem.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.golem.core.GolemCore;
import dev.golem.core.Ids;
import dev.golem.core.net.GolemCodec;
import dev.golem.core.net.GolemNetwork;
import dev.golem.core.net.GolemPayload;
import dev.golem.core.platform.Lifecycle;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * A datapack folder of JSON files that a mod reads as data, not as recipes or registries: every
 * {@code data/<any namespace>/<folder>/<path>.json}, keyed {@code <namespace>:<path>}. Golem Core reads the folder on
 * the server when it starts and after every {@code /reload}, hands the files to the mod, and sends them to every client
 * (on join and after a reload), where the mod gets them again: one place a datapack, KubeJS or another mod changes a
 * mod's data, the same on every loader and version without a loader reload listener.
 *
 * <pre>{@code
 * DataFiles.folder("fluid_property", files -> FluidProperties.apply(files));
 * }</pre>
 *
 * <p>The consumer runs on the server thread, and on a client on the client thread; in singleplayer both run, with the
 * same files. It receives every file of the folder each time, sorted by id: it replaces, it does not merge.
 */
public final class DataFiles {

    private record Folder(String path, Consumer<Map<String, JsonElement>> apply,
                          Consumer<Map<String, List<JsonElement>>> applyStacks) {

        boolean stacked() {
            return applyStacks != null;
        }
    }

    private static final Map<String, Folder> FOLDERS = new ConcurrentHashMap<>();
    private static volatile Object source;

    private DataFiles() {}

    /**
     * The datapack folder recipes live in on this node, for {@link #read} over recipe files: {@code recipe} from 1.21
     * on, {@code recipes} on 1.20.1 (its plural data directories; a mod writes {@code recipe} and the build renames it).
     */
    public static String recipeFolder() {
        //? if >=1.21 {
        return "recipe";
        //?} else {
        /*return "recipes";
        *///?}
    }

    /** Declares {@code folder}: {@code apply} receives its files after every load. Call at mod construction. */
    public static void folder(String folder, Consumer<Map<String, JsonElement>> apply) {
        if (FOLDERS.putIfAbsent(folder, new Folder(folder, apply, null)) != null) {
            throw new IllegalStateException("Data folder " + folder + " declared twice");
        }
    }

    /**
     * Declares {@code folder} read as stacks: for each id, every pack's copy of the file, lowest priority first (as
     * vanilla's tags are read), so contributions from several mods to one file merge instead of the top pack winning.
     */
    public static void stackedFolder(String folder, Consumer<Map<String, List<JsonElement>>> apply) {
        if (FOLDERS.putIfAbsent(folder, new Folder(folder, null, apply)) != null) {
            throw new IllegalStateException("Data folder " + folder + " declared twice");
        }
    }

    /** Golem Core's entrypoints call this on every side. */
    public static void init() {
        GolemNetwork.declarePlayToClient(SyncPayload.CODEC);
        Lifecycle.onServerStarted(DataFiles::refresh);
        Lifecycle.onDatapackSync((server, players) -> {
            refresh(server);
            for (Folder folder : FOLDERS.values()) {
                SyncPayload payload = payload(folder, server.getResourceManager());
                for (ServerPlayer player : players) GolemNetwork.sendToPlayer(player, SyncPayload.CODEC, payload);
            }
        });
    }

    /** Golem Core's client entrypoints call this. */
    public static void initClient() {
        GolemNetwork.registerPlayToClient(SyncPayload.CODEC, (payload, context) -> {
            Folder folder = FOLDERS.get(payload.folder());
            if (folder == null) return;
            if (folder.stacked()) {
                Map<String, List<JsonElement>> stacks = new TreeMap<>();
                for (int i = 0; i + 1 < payload.rows().size(); i += 2) {
                    List<JsonElement> stack = new ArrayList<>();
                    for (JsonElement element : JsonParser.parseString(payload.rows().get(i + 1)).getAsJsonArray()) stack.add(element);
                    stacks.put(payload.rows().get(i), stack);
                }
                folder.applyStacks().accept(stacks);
                return;
            }
            Map<String, JsonElement> files = new TreeMap<>();
            for (int i = 0; i + 1 < payload.rows().size(); i += 2) {
                files.put(payload.rows().get(i), JsonParser.parseString(payload.rows().get(i + 1)));
            }
            folder.apply().accept(files);
        });
    }

    private static synchronized void refresh(MinecraftServer server) {
        ResourceManager manager = server.getResourceManager();
        if (manager == source) return;
        source = manager;
        for (Folder folder : FOLDERS.values()) {
            if (folder.stacked()) folder.applyStacks().accept(readStacks(folder.path(), manager));
            else folder.apply().accept(read(folder.path(), manager));
        }
    }

    /** Every file of {@code folder} in {@code manager}, by {@code <namespace>:<path>}, sorted. */
    public static Map<String, JsonElement> read(String folder, ResourceManager manager) {
        Map<String, JsonElement> files = new TreeMap<>();
        var found = manager.listResources(folder, id -> id.getPath().endsWith(".json"));
        for (var entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            String id = entry.getKey().getNamespace() + ":" + path.substring(folder.length() + 1, path.length() - ".json".length());
            try (Reader reader = entry.getValue().openAsReader()) {
                files.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                GolemCore.LOG.warn("Skipping data file {} in {}: {}", id, folder, e.toString());
            }
        }
        return files;
    }

    /** Every pack's copy of every file of {@code folder}, by {@code <namespace>:<path>}, lowest priority first. */
    public static Map<String, List<JsonElement>> readStacks(String folder, ResourceManager manager) {
        Map<String, List<JsonElement>> stacks = new TreeMap<>();
        var found = manager.listResourceStacks(folder, id -> id.getPath().endsWith(".json"));
        for (var entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            String id = entry.getKey().getNamespace() + ":" + path.substring(folder.length() + 1, path.length() - ".json".length());
            List<JsonElement> stack = new ArrayList<>();
            for (var resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    stack.add(JsonParser.parseReader(reader));
                } catch (Exception e) {
                    GolemCore.LOG.warn("Skipping a copy of data file {} in {}: {}", id, folder, e.toString());
                }
            }
            stacks.put(id, stack);
        }
        return stacks;
    }

    private static SyncPayload payload(Folder folder, ResourceManager manager) {
        List<String> rows = new ArrayList<>();
        if (folder.stacked()) {
            for (Map.Entry<String, List<JsonElement>> stack : readStacks(folder.path(), manager).entrySet()) {
                com.google.gson.JsonArray array = new com.google.gson.JsonArray();
                stack.getValue().forEach(array::add);
                rows.add(stack.getKey());
                rows.add(array.toString());
            }
            return new SyncPayload(folder.path(), rows);
        }
        for (Map.Entry<String, JsonElement> file : read(folder.path(), manager).entrySet()) {
            rows.add(file.getKey());
            rows.add(file.getValue().toString());
        }
        return new SyncPayload(folder.path(), rows);
    }

    /** One folder's files for a client: id and JSON text, alternating. */
    record SyncPayload(String folder, List<String> rows) implements GolemPayload {

        static final GolemCodec<SyncPayload> CODEC = GolemCodec.of(SyncPayload.class, Ids.of("golemcore", "data_files"),
                (payload, buffer) -> {
                    buffer.writeUtf(payload.folder());
                    buffer.writeVarInt(payload.rows().size());
                    for (String row : payload.rows()) buffer.writeUtf(row, 262144);
                },
                buffer -> {
                    String folder = buffer.readUtf();
                    int size = buffer.readVarInt();
                    List<String> rows = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) rows.add(buffer.readUtf(262144));
                    return new SyncPayload(folder, rows);
                });

        @Override
        public
                //? if >=26.1 {
                net.minecraft.resources.Identifier
                //?} else {
                /*net.minecraft.resources.ResourceLocation
                *///?}
                id() {
            return CODEC.id();
        }
    }

    /**
     * {@code resource} (absolute, starting with {@code /}) from the owner's own jar (or classes folder). Fabric loads every mod through one class loader, so a
     * plain {@code getResource} can hand back another mod's file at the same path (an addon adding to HBM's map ships
     * {@code data/hbm/value_maps/...} too); the copy next to the owner's code wins, the first one found otherwise.
     */
    public static java.net.URL bundled(Class<?> owner, String resource) {
        ClassLoader loader = owner.getClassLoader();
        if (loader == null) return owner.getResource(resource);
        String own = null;
        java.net.URL classUrl = owner.getResource("/" + owner.getName().replace('.', '/') + ".class");
        if (classUrl != null) {
            String s = classUrl.toString();
            own = s.substring(0, s.length() - (owner.getName().length() + ".class".length()));
        }
        try {
            java.net.URL first = null;
            var all = loader.getResources(resource.substring(1));
            while (all.hasMoreElements()) {
                java.net.URL url = all.nextElement();
                if (first == null) first = url;
                if (own != null && url.toString().startsWith(own)) return url;
            }
            return first;
        } catch (java.io.IOException e) {
            return owner.getResource(resource);
        }
    }
}
