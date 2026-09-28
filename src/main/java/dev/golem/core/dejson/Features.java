package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * World-generation features declared in code: {@code worldgen/configured_feature} and
 * {@code worldgen/placed_feature} files, plus the placement-modifier and configuration vocabulary they are
 * written in.
 *
 * <p>Everything here is spelled the way 1.20.1, 1.21.1 and 26.1 all read it — {@code count},
 * {@code rarity_filter}, {@code in_square}, {@code height_range} over a {@code uniform} height provider,
 * {@code heightmap}, {@code biome}, and the {@code ore} configuration's rule tests — so one declaration is
 * valid data on every node. A shape that changed between versions (26.1 dissolved {@code random_patch} into
 * placement modifiers, for instance) does not belong in this vocabulary until it has a per-version spelling
 * here.
 *
 * <p>Like the rest of {@code dejson}, nothing in this class touches a Minecraft class, so the declarations can
 * be written out by a plain JVM at build time.
 */
public final class Features {

    private Features() {}

    // ------------------------------------------------------------------------------------------------ files

    /** {@code data/<namespace>/worldgen/configured_feature/<name>.json}. */
    public static JsonObject configured(String namespace, String name, String type, JsonObject config) {
        requireName(namespace, name);
        Models.requireId(type, "feature type");
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.add("config", config == null ? new JsonObject() : config);
        return Data.declare("data/" + namespace + "/worldgen/configured_feature/" + name + ".json", json);
    }

    /**
     * {@code data/<namespace>/worldgen/placed_feature/<name>.json}: {@code feature} (a configured-feature id)
     * run through {@code placement}, in order. No modifier means once per chunk at the chunk's corner.
     */
    public static JsonObject placed(String namespace, String name, String feature, JsonObject... placement) {
        requireName(namespace, name);
        Models.requireId(feature, "configured feature");
        JsonArray modifiers = new JsonArray();
        for (JsonObject modifier : placement) modifiers.add(modifier);
        JsonObject json = new JsonObject();
        json.addProperty("feature", feature);
        json.add("placement", modifiers);
        return Data.declare("data/" + namespace + "/worldgen/placed_feature/" + name + ".json", json);
    }

    private static void requireName(String namespace, String name) {
        if (namespace == null || namespace.isEmpty() || name == null || name.isEmpty()) {
            throw new DejsonException("a feature needs a namespace and a name");
        }
    }

    // ------------------------------------------------------------------------------------------------ placement

    /** {@code count}: the position repeated {@code n} times. */
    public static JsonObject count(int n) {
        if (n < 1) throw new DejsonException("count " + n + " places nothing; leave the feature out instead");
        JsonObject json = modifier("count");
        json.addProperty("count", n);
        return json;
    }

    /** {@code rarity_filter}: kept with probability {@code 1/chance}, i.e. the old {@code rand.nextInt(chance) == 0}. */
    public static JsonObject rarity(int chance) {
        if (chance < 1) throw new DejsonException("rarity " + chance + " is not a chance");
        JsonObject json = modifier("rarity_filter");
        json.addProperty("chance", chance);
        return json;
    }

    /** {@code in_square}: a random x and z inside the chunk. */
    public static JsonObject inSquare() {
        return modifier("in_square");
    }

    /** {@code biome}: dropped unless the biome at the position lists this feature. */
    public static JsonObject biome() {
        return modifier("biome");
    }

    /** {@code heightmap}: y moved to the top of the named heightmap ({@code MOTION_BLOCKING}, {@code WORLD_SURFACE_WG}, ...). */
    public static JsonObject heightmap(String heightmap) {
        JsonObject json = modifier("heightmap");
        json.addProperty("heightmap", heightmap);
        return json;
    }

    /** {@code height_range} over a {@code uniform} provider: y uniformly in {@code [min, max]}, both inclusive. */
    public static JsonObject uniformHeight(JsonObject min, JsonObject max) {
        JsonObject height = new JsonObject();
        height.addProperty("type", "minecraft:uniform");
        height.add("min_inclusive", min);
        height.add("max_inclusive", max);
        JsonObject json = modifier("height_range");
        json.add("height", height);
        return json;
    }

    /** A vertical anchor at an absolute y. */
    public static JsonObject absolute(int y) {
        return anchor("absolute", y);
    }

    /** A vertical anchor {@code offset} blocks above the dimension's lowest y. */
    public static JsonObject aboveBottom(int offset) {
        return anchor("above_bottom", offset);
    }

    private static JsonObject anchor(String kind, int value) {
        JsonObject json = new JsonObject();
        json.addProperty(kind, value);
        return json;
    }

    private static JsonObject modifier(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:" + type);
        return json;
    }

    // ------------------------------------------------------------------------------------------------ configuration

    /** A block state, {@code properties} given as name/value pairs. */
    public static JsonObject state(String block, String... properties) {
        Models.requireId(block, "block");
        if (properties.length % 2 != 0) throw new DejsonException(block + ": properties come in name/value pairs");
        JsonObject json = new JsonObject();
        json.addProperty("Name", block);
        if (properties.length > 0) {
            JsonObject props = new JsonObject();
            for (int i = 0; i < properties.length; i += 2) props.addProperty(properties[i], properties[i + 1]);
            json.add("Properties", props);
        }
        return json;
    }

    /** Rule test {@code block_match}: exactly this block. */
    public static JsonObject blockMatch(String block) {
        Models.requireId(block, "block");
        JsonObject json = new JsonObject();
        json.addProperty("predicate_type", "minecraft:block_match");
        json.addProperty("block", block);
        return json;
    }

    /** Rule test {@code tag_match}: any block in the tag (no leading {@code #}). */
    public static JsonObject tagMatch(String tag) {
        Models.requireId(tag, "block tag");
        JsonObject json = new JsonObject();
        json.addProperty("predicate_type", "minecraft:tag_match");
        json.addProperty("tag", tag);
        return json;
    }

    /** The {@code ore} configuration shape: one target, one state, a size; nothing discarded on air exposure. */
    public static JsonObject ore(JsonObject target, JsonObject state, int size) {
        JsonObject entry = new JsonObject();
        entry.add("target", target);
        entry.add("state", state);
        JsonArray targets = new JsonArray();
        targets.add(entry);
        JsonObject json = new JsonObject();
        json.add("targets", targets);
        json.addProperty("size", size);
        json.addProperty("discard_chance_on_air_exposure", 0.0F);
        return json;
    }

    /** The {@code state}-only configuration ({@code BlockStateConfiguration}). */
    public static JsonObject blockState(JsonObject state) {
        JsonObject json = new JsonObject();
        json.add("state", state);
        return json;
    }

    // ------------------------------------------------------------------------------------------------ biome tags

    /**
     * {@code data/<namespace>/tags/worldgen/biome/<path>.json}. {@code optional} members are written as
     * {@code {"id": ..., "required": false}} so a biome a version does not have (the pale garden before 1.21.4)
     * does not fail the whole tag.
     */
    public static JsonObject biomeTag(String namespace, String path, String[] required, String... optional) {
        requireName(namespace, path);
        JsonArray values = new JsonArray();
        for (String id : required) {
            Models.requireId(id.startsWith("#") ? id.substring(1) : id, "biome");
            values.add(id);
        }
        for (String id : optional) {
            Models.requireId(id.startsWith("#") ? id.substring(1) : id, "biome");
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("required", false);
            values.add(entry);
        }
        if (values.isEmpty()) throw new DejsonException("biome tag " + namespace + ":" + path + " lists nothing");
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", values);
        return Data.declare("data/" + namespace + "/tags/worldgen/biome/" + path + ".json", tag);
    }
}
