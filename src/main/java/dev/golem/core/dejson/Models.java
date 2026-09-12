package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.golem.core.render.display.DisplayFrame;
import dev.golem.core.render.display.DisplaySlot;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
     * Several suffixed models under one declaration, for a block whose blockstate picks between
     * them: the on/off pair a machine switches, the numbered pool a shaded ore draws from.
     *
     * <p>{@link Variants#slab} and {@link Variants#stairs} have their own presets because their
     * suffixes are fixed and their parents are irregular. This is for everything else, where the
     * suffix is the blockstate's business and each entry is an ordinary shape.
     *
     * <pre>{@code
     * Models.group().and("_off", Models.cubeAll("hbm:blocks/lamp_off"))
     *               .and("_on",  Models.cubeAll("hbm:blocks/lamp_on"))
     * }</pre>
     *
     * <p>The empty suffix is allowed and means the base model itself, because a blockstate that
     * picks between {@code <base>} and {@code <base>_cracked} is the same kind of thing as one
     * picking between {@code _off} and {@code _on}.
     */
    public static Group group() {
        return new Group(Collections.emptyMap());
    }

    /** A set of suffixed sub-shapes, built up one at a time. Immutable; {@link #and} returns a new one. */
    public static final class Group implements Shape {

        private final Map<String, Shape> parts;

        private Group(Map<String, Shape> parts) {
            this.parts = parts;
        }

        public Group and(String suffix, Shape shape) {
            if (suffix == null) {
                throw new DejsonException("a group entry needs a suffix");
            }
            if (shape == null) {
                throw new DejsonException("group entry '" + suffix + "' was given a null shape");
            }
            if (parts.containsKey(suffix)) {
                throw new DejsonException("group entry '" + suffix + "' declared twice");
            }
            Map<String, Shape> merged = new LinkedHashMap<>(parts);
            merged.put(suffix, shape);
            return new Group(Collections.unmodifiableMap(merged));
        }

        @Override
        public Map<String, JsonObject> models(String base) {
            if (parts.isEmpty()) {
                throw new DejsonException("group() with no entries emits nothing; "
                        + "use Models.none() if that is what was meant");
            }
            Map<String, JsonObject> models = new LinkedHashMap<>();
            parts.forEach((suffix, shape) -> {
                Map<String, JsonObject> part = shape.models(base + suffix);
                if (part.size() != 1 || !part.containsKey(base + suffix)) {
                    throw new DejsonException("group entry '" + suffix
                            + "' emits more than its own model: " + part.keySet());
                }
                models.putAll(part);
            });
            return Collections.unmodifiableMap(models);
        }
    }

    /**
     * The same shape, tagged with the render type its block needs.
     *
     * <p>{@code render_type} is how a model asks for anything but the opaque solid layer — cutout
     * for a block with holes in its texture, translucent for glass. It is a model field rather
     * than a block property on every target here, and leaving it off does not fail the bake: it
     * draws the block on the solid layer, where its transparent pixels come out black.
     *
     * <pre>{@code Models.renderType("minecraft:cutout", Models.cubeAll("hbm:blocks/gas_radon"))}</pre>
     */
    public static Shape renderType(String renderType, Shape shape) {
        if (renderType == null || renderType.isEmpty()) {
            throw new DejsonException("renderType() needs a render type");
        }
        requireId(renderType, "render type");
        if (shape == null) {
            throw new DejsonException("renderType('" + renderType + "') was given a null shape");
        }
        return base -> {
            Map<String, JsonObject> tagged = new LinkedHashMap<>();
            shape.models(base).forEach((id, model) -> {
                JsonObject copy = model.deepCopy();
                copy.addProperty("render_type", renderType);
                tagged.put(id, copy);
            });
            return Collections.unmodifiableMap(tagged);
        };
    }

    /**
     * The same shape, wearing the {@code display{}} block a set of poses composes to.
     *
     * <p>A {@code display{}} entry is not a matrix — it is a translate/rotate/scale triple vanilla
     * applies whatever it says. {@link DisplayFrame} does the conversion and refuses the three shapes
     * vanilla would corrupt in silence, so a pose either ships exactly or fails the run.
     *
     * <pre>{@code Models.display(Pose17.frames(Pose17.Klass.ITEM_RENDER_BASE),
     *         Models.parented("minecraft:block/block", Slots.of("particle", "hbm:blocks/block_steel")))}</pre>
     */
    public static Shape display(Map<DisplaySlot, Matrix4f> poses, Shape shape) {
        if (poses == null || poses.isEmpty()) {
            throw new DejsonException("display() needs at least one posed slot");
        }
        if (shape == null) {
            throw new DejsonException("display() was given a null shape");
        }
        return base -> {
            JsonObject display = displayJson(base, poses);
            Map<String, JsonObject> posed = new LinkedHashMap<>();
            shape.models(base).forEach((id, model) -> {
                JsonObject copy = model.deepCopy();
                copy.add("display", display.deepCopy());
                posed.put(id, copy);
            });
            return Collections.unmodifiableMap(posed);
        };
    }

    /**
     * The same shape, stating which lighting an inventory icon is drawn under. {@code side} is the
     * three-quarter lighting a block gets; {@code front} is the flat lighting a sprite gets.
     */
    public static Shape guiLight(String guiLight, Shape shape) {
        if (!"side".equals(guiLight) && !"front".equals(guiLight)) {
            throw new DejsonException("guiLight() takes \"side\" or \"front\", not " + guiLight);
        }
        if (shape == null) {
            throw new DejsonException("guiLight('" + guiLight + "') was given a null shape");
        }
        return base -> {
            Map<String, JsonObject> lit = new LinkedHashMap<>();
            shape.models(base).forEach((id, model) -> {
                JsonObject copy = model.deepCopy();
                copy.addProperty("gui_light", guiLight);
                lit.put(id, copy);
            });
            return Collections.unmodifiableMap(lit);
        };
    }

    private static JsonObject displayJson(String base, Map<DisplaySlot, Matrix4f> poses) {
        JsonObject display = new JsonObject();
        DisplayFrame.of(base, poses).forEach((slot, frame) -> {
            JsonObject transform = new JsonObject();
            transform.add("rotation", triple(frame.rotation()));
            transform.add("translation", triple(frame.translation()));
            transform.add("scale", triple(frame.scale()));
            display.add(slot.serializedName(), transform);
        });
        return display;
    }

    private static JsonArray triple(Vector3d value) {
        JsonArray array = new JsonArray();
        array.add(value.x());
        array.add(value.y());
        array.add(value.z());
        return array;
    }

    /**
     * A shape that emits nothing, for a block whose appearance comes from a renderer.
     *
     * <p>Paired with {@link Dejson.Declaration#rendered}, which points the blockstate at a shared
     * model somebody else owns. 150 of the blockstates shipped here are exactly that: a machine
     * drawn by a block-entity renderer, whose blockstate exists only because vanilla demands one
     * and whose model is the same empty placeholder every time.
     */
    public static Shape none() {
        return base -> Collections.emptyMap();
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
        return itemDefinition(modelId, List.of());
    }

    /** The same definition with a tint list layered over the model it names. */
    public static JsonObject itemDefinition(String modelId, List<Tint> tints) {
        requireId(modelId, "item model");
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", modelId);
        if (!tints.isEmpty()) {
            JsonArray entries = new JsonArray();
            for (Tint tint : tints) {
                entries.add(tint.toJson());
            }
            model.add("tints", entries);
        }
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
