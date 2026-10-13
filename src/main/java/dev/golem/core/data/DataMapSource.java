package dev.golem.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.golem.core.world.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;

/**
 * One {@link DataMaps} file written from Java at build time (a {@link DataSource}, see
 * {@link dev.golem.core.world.DataGenerator}): the map's own file ({@code value_maps/<registry>/<map>.json}) or, with
 * {@code patch}, a patch of another mod's map ({@code value_maps/patches/<map ns>/<registry>/<map>.json}). Values are
 * encoded with the map's own value codec. A subclass adds them in {@code registerDefaults()} with {@link #put} (an entry
 * id or a {@code #tag}), {@link #removeEntry} and {@link #replace}, in file order.
 *
 * @param <V> the map's value type
 */
public abstract class DataMapSource<V> extends DataSource<DataMapSource.File<V>> {

    /** One map file: {@code replace}, the values by entry id or {@code #tag} in file order, and {@code remove}. */
    public record File<V>(boolean replace, Map<String, V> values, List<String> remove) {}

    private final DataMaps.DataMap<?, V> map;
    private final Map<String, V> values = new LinkedHashMap<>();
    private final List<String> removed = new ArrayList<>();
    private boolean replace;

    /** The map's own file ({@code patch} false) or this mod's patch of it. */
    protected DataMapSource(DataMaps.DataMap<?, V> map, boolean patch) {
        super(DataMaps.FOLDER + "/" + (patch ? DataMaps.PATCHES + "/" + map.namespace() + "/" : "") + map.registryPath());
        this.map = map;
    }

    /** Adds the values; called once ({@link #put}, {@link #removeEntry}, {@link #replace}). */
    protected abstract void values();

    @Override
    protected final void registerDefaults() {
        values();
        register(map.path(), new File<>(replace, new LinkedHashMap<>(values), List.copyOf(removed)));
    }

    /** {@code entry} (an id, or {@code #tag}) maps to {@code value}; a later put of the same key replaces it. */
    protected final void put(String entry, V value) {
        values.put(entry, value);
    }

    /** Takes away what lower-priority packs gave {@code entry} (an id or {@code #tag}). */
    protected final void removeEntry(String entry) {
        removed.add(entry);
    }

    /** Drops everything lower-priority packs gave the map. */
    protected final void replace() {
        replace = true;
    }

    @Override
    public JsonElement encode(File<V> file, HolderLookup.Provider registries) {
        JsonObject json = new JsonObject();
        if (file.replace()) json.addProperty("replace", true);
        if (!file.values().isEmpty()) {
            JsonObject values = new JsonObject();
            for (Map.Entry<String, V> value : file.values().entrySet()) {
                values.add(value.getKey(), unwrap(map.codec().encodeStart(JsonOps.INSTANCE, value.getValue()), value.getKey()));
            }
            json.add("values", values);
        }
        if (!file.remove().isEmpty()) {
            JsonArray remove = new JsonArray();
            file.remove().forEach(remove::add);
            json.add("remove", remove);
        }
        return json;
    }

    @Override
    public File<V> decode(JsonElement element, HolderLookup.Provider registries) {
        JsonObject json = element.getAsJsonObject();
        Map<String, V> values = new LinkedHashMap<>();
        if (json.has("values")) {
            for (Map.Entry<String, JsonElement> value : json.getAsJsonObject("values").entrySet()) {
                values.put(value.getKey(), unwrap(map.codec().parse(JsonOps.INSTANCE, value.getValue()), value.getKey()));
            }
        }
        List<String> remove = new ArrayList<>();
        if (json.has("remove")) for (JsonElement id : json.getAsJsonArray("remove")) remove.add(id.getAsString());
        return new File<>(json.has("replace") && json.get("replace").getAsBoolean(), values, remove);
    }

    /** The value, or the codec's message (DFU's no-argument {@code getOrThrow} does not exist below 1.20.5). */
    private <T> T unwrap(DataResult<T> result, String entry) {
        return result.result().orElseThrow(() -> new IllegalStateException(describe() + " " + entry + ": "
                + result.error().map(error -> error.message()).orElse("?")));
    }

    @Override
    public String describe() {
        return "data map " + map.id() + " (" + folder() + ")";
    }
}
