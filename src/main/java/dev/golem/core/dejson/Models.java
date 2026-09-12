package dev.golem.core.dejson;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The model half of the declaration vocabulary: the parented, texture-only models that make up
 * almost every block model shipped.
 *
 * <p>The vocabulary is deliberately closed. Anything carrying hand-built {@code elements} stays a
 * file, and anything with real geometry is an OBJ and belongs to the instanced renderer — neither
 * is a case this layer tries to express.
 *
 * <p>The {@code *All} variants exist because the overwhelming majority of slabs and stairs repeat
 * one texture across {@code bottom}/{@code side}/{@code top}, and because their double-slab model
 * is then a {@code cube_all} rather than a {@code cube_bottom_top}. Both spellings bake the same,
 * but only one matches what is on disk today, and matching lets a hand-written file be deleted
 * without anyone having to look at the result.
 */
public final class Models {

    public static final String CUBE_ALL = "minecraft:block/cube_all";
    public static final String CUBE_BOTTOM_TOP = "minecraft:block/cube_bottom_top";
    public static final String CUBE_COLUMN = "minecraft:block/cube_column";
    public static final String ORIENTABLE = "minecraft:block/orientable";
    public static final String CROSS = "minecraft:block/cross";
    public static final String SLAB = "minecraft:block/slab";
    public static final String SLAB_TOP = "minecraft:block/slab_top";
    public static final String STAIRS = "minecraft:block/stairs";
    public static final String INNER_STAIRS = "minecraft:block/inner_stairs";
    public static final String OUTER_STAIRS = "minecraft:block/outer_stairs";
    public static final String ITEM_GENERATED = "minecraft:item/generated";
    public static final String ITEM_HANDHELD = "minecraft:item/handheld";

    private Models() {}

    /** One model with an explicit parent and slot set; the escape hatch below the named presets. */
    public static Shape parented(String parent, Slots slots) {
        requireId(parent, "parent");
        return base -> single(base, json(parent, slots));
    }

    public static Shape cubeAll(String all) {
        return parented(CUBE_ALL, Slots.of("all", all));
    }

    public static Shape cubeBottomTop(String bottom, String side, String top) {
        return parented(CUBE_BOTTOM_TOP, Slots.of("bottom", bottom, "side", side, "top", top));
    }

    /** A pillar: {@code end} caps and a {@code side} wrap, oriented by {@link Variants#axis()}. */
    public static Shape column(String end, String side) {
        return parented(CUBE_COLUMN, Slots.of("end", end, "side", side));
    }

    public static Shape orientable(String front, String side, String top) {
        return parented(ORIENTABLE, Slots.of("front", front, "side", side, "top", top));
    }

    public static Shape cross(String cross) {
        return parented(CROSS, Slots.of("cross", cross));
    }

    /** Bottom, top and double slab from one texture; the double is a {@code cube_all}. */
    public static Shape slabAll(String all) {
        return slab(Slots.of("bottom", all, "side", all, "top", all), cubeAllJson(all));
    }

    public static Shape slab(String bottom, String side, String top) {
        Slots slots = Slots.of("bottom", bottom, "side", side, "top", top);
        return slab(slots, json(CUBE_BOTTOM_TOP, slots));
    }

    /** Straight, inner and outer stair models from one texture. */
    public static Shape stairsAll(String all) {
        return stairs(Slots.of("bottom", all, "side", all, "top", all));
    }

    public static Shape stairs(String bottom, String side, String top) {
        return stairs(Slots.of("bottom", bottom, "side", side, "top", top));
    }

    public static Shape generated(String... layers) {
        if (layers.length == 0) {
            throw new DejsonException("generated() needs at least one layer texture");
        }
        Slots slots = Slots.EMPTY;
        for (int layer = 0; layer < layers.length; layer++) {
            slots = slots.and("layer" + layer, layers[layer]);
        }
        return parented(ITEM_GENERATED, slots);
    }

    public static Shape handheld(String layer0) {
        return parented(ITEM_HANDHELD, Slots.of("layer0", layer0));
    }

    /**
     * The item model of a block: a bare parent pointing at the block's own model, with no textures
     * of its own. This is the only item shape whose parent is not a vanilla one, so it takes the
     * block model id rather than deriving it — a block whose item should show a different model
     * than the block itself is common enough that guessing would be wrong.
     */
    public static Shape blockItem(String blockModel) {
        requireId(blockModel, "block model");
        return base -> single(base, json(blockModel, Slots.EMPTY));
    }

    private static Shape slab(Slots slots, JsonObject doubleModel) {
        return base -> {
            Map<String, JsonObject> models = new LinkedHashMap<>();
            models.put(base, json(SLAB, slots));
            models.put(base + Variants.TOP_SUFFIX, json(SLAB_TOP, slots));
            models.put(base + Variants.DOUBLE_SUFFIX, doubleModel);
            return Collections.unmodifiableMap(models);
        };
    }

    private static Shape stairs(Slots slots) {
        return base -> {
            Map<String, JsonObject> models = new LinkedHashMap<>();
            models.put(base, json(STAIRS, slots));
            models.put(base + Variants.INNER_SUFFIX, json(INNER_STAIRS, slots));
            models.put(base + Variants.OUTER_SUFFIX, json(OUTER_STAIRS, slots));
            return Collections.unmodifiableMap(models);
        };
    }

    private static JsonObject cubeAllJson(String all) {
        return json(CUBE_ALL, Slots.of("all", all));
    }

    /**
     * The modern item-model <i>definition</i>, which is a different file from the item model: from
     * 26.1 an item is {@code assets/<ns>/items/<path>.json} naming a model, and the model itself
     * stays at {@code models/item/<path>.json} for older targets to load directly. 1895 of the 2196
     * definitions shipped are exactly this shape, which is why one declaration can emit both.
     */
    public static JsonObject itemDefinition(String modelId) {
        requireId(modelId, "item model");
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", modelId);
        JsonObject definition = new JsonObject();
        definition.add("model", model);
        return definition;
    }

    static JsonObject json(String parent, Slots slots) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", parent);
        if (!slots.isEmpty()) {
            model.add("textures", slots.toJson());
        }
        return model;
    }

    private static Map<String, JsonObject> single(String base, JsonObject model) {
        return Collections.singletonMap(base, model);
    }

    static void requireId(String id, String what) {
        if (id == null || id.isEmpty()) {
            throw new DejsonException(what + " id is missing");
        }
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = id.substring(colon + 1);
        if (namespace.isEmpty() || path.isEmpty() || id.indexOf(':', colon + 1) >= 0) {
            throw new DejsonException(what + " id '" + id + "' is not a namespaced path");
        }
    }
}
