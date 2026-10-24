package dev.golem.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.golem.core.GolemCore;
import dev.golem.core.Ids;
import dev.golem.core.hook.Hook;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/**
 * Values attached to registry entries as data (NeoForge's data maps, on every loader and version): a mod declares a
 * map once, everyone fills it with JSON at {@code data/<map namespace>/value_maps/<registry path>/<map path>.json}, and
 * the mod reads {@code map.get(entry)}.
 *
 * <pre>
 * {
 *   "replace": false,                         true drops what lower-priority packs gave the map
 *   "values": {"hbm:ingot_uranium": {...}, "#c:ingots/plutonium": {...}},   an entry id or a #tag, and the value
 *   "remove": ["hbm:ingot_u235"]              entries (or #tags) lower packs gave that this pack takes away
 * }
 * </pre>
 *
 * Every pack's copy of the file merges, lowest priority first (as tags do), so HBM, two addons and a server datapack
 * can all add to one map without one file replacing another. An entry's own id beats a tag; among tags, the last one
 * given wins. The owning mod's file is also read from its jar when the map is declared ({@link #map}), so the values
 * exist before any world (menus, tooltips); every datapack load replaces them with the merged datapack values, and
 * clients receive the same files.
 *
 * <p><b>Changing another mod's map.</b> A file at the map's own path only merges in pack order, and pack order between
 * mods is not defined everywhere (Fabric 1.20.1 serves every mod from one group pack, in the loader's mod order). A mod
 * that changes another mod's map, especially with {@code "remove"} or by overriding one of its values, puts a patch under
 * its own namespace instead: {@code data/<own ns>/value_maps/patches/<map namespace>/<registry path>/<map path>.json},
 * the same format. Patches apply after every copy of the map's own file, by patch namespace (sorted), each namespace's
 * copies lowest priority first (as {@code recipe_removals} and the fluid property patches).
 */
public final class DataMaps {

    /** The datapack folder. */
    public static final String FOLDER = "value_maps";

    /** The sub-folder of {@link #FOLDER}, under the patching mod's namespace, that holds patches of other maps. */
    public static final String PATCHES = "patches";

    private static final Map<String, DataMap<?, ?>> MAPS = new ConcurrentHashMap<>();

    static {
        DataFiles.stackedFolder(FOLDER, DataMaps::load);
    }

    private DataMaps() {}

    /**
     * Declares the map {@code <namespace>:<path>} over {@code registry} (e.g. {@code () -> BuiltInRegistries.ITEM},
     * registry path {@code "item"}), decoding values with {@code codec}. {@code owner} is any class of the declaring
     * mod: its own file is read from that mod's jar now. Call at mod construction.
     */
    public static <T, V> DataMap<T, V> map(Class<?> owner, String namespace, String path, String registryPath,
                                           Supplier<? extends Registry<T>> registry, Codec<V> codec) {
        return declare(owner, new DataMap<>(namespace, path, registryPath, registry, () -> registry.get().key(), codec));
    }

    /** Declares a map over a datapack registry ({@code () -> Registries.BIOME}, {@code "worldgen/biome"}), read by holder. */
    public static <T, V> DataMap<T, V> dynamic(Class<?> owner, String namespace, String path, String registryPath,
                                               Supplier<ResourceKey<? extends Registry<T>>> registryKey, Codec<V> codec) {
        return declare(owner, new DataMap<>(namespace, path, registryPath, null, registryKey, codec));
    }

    private static <T, V> DataMap<T, V> declare(Class<?> owner, DataMap<T, V> map) {
        if (MAPS.putIfAbsent(map.fileId(), map) != null) throw new IllegalStateException("Data map " + map.fileId() + " declared twice");
        String resource = "/data/" + map.namespace + "/" + FOLDER + "/" + map.registryPath + "/" + map.path + ".json";
        URL url = DataFiles.bundled(owner, resource);
        if (url != null) {
            try (InputStream in = url.openStream()) {
                map.load(List.of(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))));
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("The bundled data map " + resource + " is broken: " + e.getMessage(), e);
            }
        }
        return map;
    }

    /**
     * Loads every map from {@code stacks} (file id inside {@link #FOLDER} to each pack's copy, lowest priority first), as
     * a datapack load does; a test adds a pack's file to the stacks a server read and loads them again.
     */
    public static void load(Map<String, List<JsonElement>> stacks) {
        for (DataMap<?, ?> map : MAPS.values()) {
            List<JsonElement> stack = stacks.get(map.fileId());
            List<JsonElement> all = new ArrayList<>(stack == null ? List.of() : stack);
            // the patches of this map, by patch namespace (the stacks are sorted by id)
            String patch = ":" + PATCHES + "/" + map.namespace + "/" + map.registryPath + "/" + map.path;
            for (Map.Entry<String, List<JsonElement>> entry : stacks.entrySet()) {
                if (entry.getKey().endsWith(patch) && entry.getKey().indexOf(':') == entry.getKey().length() - patch.length()) {
                    all.addAll(entry.getValue());
                }
            }
            map.load(all);
        }
    }

    /** One map: entry id or tag to value. */
    public static final class DataMap<T, V> {

        private final String namespace;
        private final String path;
        private final String registryPath;
        private final Supplier<? extends Registry<T>> registry;
        private final Supplier<? extends ResourceKey<? extends Registry<T>>> registryKey;
        private final Codec<V> codec;
        private volatile Map<String, V> byId = Map.of();
        private volatile List<Map.Entry<String, V>> byTag = List.of();

        /** Fires after the values changed (a datapack load, a client sync), on that side's thread. */
        public final Hook<Runnable> changed = Hook.notifying();

        private DataMap(String namespace, String path, String registryPath, Supplier<? extends Registry<T>> registry,
                        Supplier<? extends ResourceKey<? extends Registry<T>>> registryKey, Codec<V> codec) {
            this.namespace = namespace;
            this.path = path;
            this.registryPath = registryPath;
            this.registry = registry;
            this.registryKey = registryKey;
            this.codec = codec;
        }

        /** {@code <namespace>:<registry path>/<path>}, the file's id inside {@link #FOLDER}. */
        public String fileId() {
            return namespace + ":" + registryPath + "/" + path;
        }

        /** {@code <namespace>:<path>}. */
        public String id() {
            return namespace + ":" + path;
        }

        /** The declaring mod's namespace. */
        public String namespace() {
            return namespace;
        }

        /** The map's path ({@code hazards}). */
        public String path() {
            return path;
        }

        /** The registry folder the map's files are under ({@code item}). */
        public String registryPath() {
            return registryPath;
        }

        /** The codec of one value. */
        public Codec<V> codec() {
            return codec;
        }

        /** The value for {@code entry}: its own id first, then the last tag given that holds it; null if none. */
        public V get(T entry) {
            if (entry == null) return null;
            if (registry == null) throw new IllegalStateException("Data map " + id() + " is over a datapack registry: read it by holder");
            Registry<T> reg = registry.get();
            var key = reg.getKey(entry);
            if (key != null) {
                V own = byId.get(key.toString());
                if (own != null) return own;
            }
            List<Map.Entry<String, V>> tags = byTag;
            if (tags.isEmpty()) return null;
            var holder = reg.wrapAsHolder(entry);
            for (int i = tags.size() - 1; i >= 0; i--) {
                TagKey<T> tag = TagKey.create(reg.key(), Ids.parse(tags.get(i).getKey()));
                if (holder.is(tag)) return tags.get(i).getValue();
            }
            return null;
        }

        /** The value for a registry holder: its own id first, then the last tag given that holds it; null if none. */
        public V get(Holder<T> holder) {
            var key = holder.unwrapKey();
            if (key.isPresent()) {
                V own = byId.get(Ids.keyId(key.get()).toString());
                if (own != null) return own;
            }
            List<Map.Entry<String, V>> tags = byTag;
            for (int i = tags.size() - 1; i >= 0; i--) {
                if (holder.is(TagKey.create(registryKey.get(), Ids.parse(tags.get(i).getKey())))) return tags.get(i).getValue();
            }
            return null;
        }

        /** Every value given to an entry id (not tags), by id, in file order (lowest-priority pack first). */
        public Map<String, V> byId() {
            return byId;
        }

        /** Every value given to a tag, by tag id, in the order they apply. */
        public List<Map.Entry<String, V>> byTag() {
            return byTag;
        }

        /** Replaces the values with the merge of {@code stack}, lowest priority first, as a datapack load does. */
        public synchronized void load(List<JsonElement> stack) {
            Map<String, V> ids = new LinkedHashMap<>();
            Map<String, V> tags = new LinkedHashMap<>();
            for (JsonElement element : stack) {
                if (!element.isJsonObject()) continue;
                JsonObject file = element.getAsJsonObject();
                if (file.has("replace") && file.get("replace").getAsBoolean()) {
                    ids.clear();
                    tags.clear();
                }
                if (file.has("values")) {
                    for (Map.Entry<String, JsonElement> value : file.getAsJsonObject("values").entrySet()) {
                        var decoded = codec.parse(JsonOps.INSTANCE, value.getValue());
                        if (decoded.result().isEmpty()) {
                            GolemCore.LOG.warn("Data map {}: skipping {}: {}", id(), value.getKey(),
                                    decoded.error().map(e -> e.message()).orElse("?"));
                            continue;
                        }
                        if (value.getKey().startsWith("#")) tags.put(value.getKey().substring(1), decoded.result().get());
                        else ids.put(value.getKey(), decoded.result().get());
                    }
                }
                if (file.has("remove")) {
                    for (JsonElement removed : file.getAsJsonArray("remove")) {
                        String key = removed.getAsString();
                        if (key.startsWith("#")) tags.remove(key.substring(1));
                        else ids.remove(key);
                    }
                }
            }
            byId = Collections.unmodifiableMap(ids);
            byTag = List.copyOf(new ArrayList<>(tags.entrySet()).stream().map(e -> Map.entry(e.getKey(), e.getValue())).toList());
            for (Runnable listener : changed) listener.run();
        }
    }
}
