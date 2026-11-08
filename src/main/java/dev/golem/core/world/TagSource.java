package dev.golem.core.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.HolderLookup;

/**
 * Tag files computed on the generating server ({@link DataGenerator}) from runtime data, in any namespace
 * ({@code data/<tag namespace>/tags/<registry>/<path>.json}). {@code generated-data.gradle.kts} merges one with a declared
 * or authored tag of the same path.
 */
public abstract class TagSource extends DataSource<List<String>> {

    private final Map<String, TreeSet<String>> members = new TreeMap<>();
    private final Map<String, String> since = new TreeMap<>();

    protected TagSource() {
        super("tags");
    }

    protected abstract void tags();

    @Override
    protected final void registerDefaults() {
        tags();
        for (Map.Entry<String, TreeSet<String>> tag : members.entrySet()) register(tag.getKey(), new ArrayList<>(tag.getValue()));
    }

    /**
     * Adds members ({@code ns:path} ids, or {@code #ns:path} tag references) to the {@code registry} tag {@code tag}
     * ({@code ns:path}). A tag named with no members is still written, so it exists on every node.
     */
    protected final void add(String registry, String tag, Collection<String> values) {
        members.computeIfAbsent(row(registry, tag), ignored -> new TreeSet<>()).addAll(values);
    }

    /** The tag exists from Minecraft {@code version} on; older nodes do not get the file. */
    protected final void since(String registry, String tag, String version) {
        since.put(row(registry, tag), version);
    }

    final Map<String, String> since() {
        return since;
    }

    private static String row(String registry, String tag) {
        int colon = tag.indexOf(':');
        if (colon < 1) throw new IllegalArgumentException("not a tag id: " + tag);
        return registry + "/" + tag.substring(0, colon) + "/" + tag.substring(colon + 1);
    }

    /** The datapack path of a row, relative to {@code data/}. */
    static String file(String row) {
        String[] parts = row.split("/", 3);
        return parts[1] + "/tags/" + parts[0] + "/" + parts[2] + ".json";
    }

    @Override
    public JsonElement encode(List<String> values, HolderLookup.Provider registries) {
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        tag.add("values", array);
        return tag;
    }

    @Override
    public List<String> decode(JsonElement json, HolderLookup.Provider registries) {
        List<String> values = new ArrayList<>();
        json.getAsJsonObject().getAsJsonArray("values").forEach(value -> values.add(value.getAsString()));
        return values;
    }
}
