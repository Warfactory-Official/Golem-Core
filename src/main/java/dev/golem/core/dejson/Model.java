package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A model spelled out in full: the parent, texture slots and flags of {@link Models}' presets, plus
 * the hand-built {@code elements} geometry, raw {@code display} transforms and legacy
 * {@code overrides} that the presets deliberately leave out.
 *
 * <pre>{@code
 * Model.parent("minecraft:block/block")
 *      .textures(Slots.of("all", "hbm:blocks/pipe_anchor", "particle", "hbm:blocks/pipe_anchor"))
 *      .elements(Element.box(6, 0, 6, 10, 16, 10)
 *              .face(Face.of("north", "#all").uv(6, 0, 10, 16))
 *              .face(Face.of("up", "#all").cullface("up")))
 * }</pre>
 *
 * <p>The presets stay the way to say the common thing. This is the way to say the uncommon one in
 * the same place, so that a block whose geometry is its own is declared like every other block
 * instead of being the one file under {@code models/} someone has to remember is hand-written.
 * Numbers keep the Java type they are written with: {@code 8} stays an integer, {@code 8.5} a
 * decimal, exactly as the file would have had them.
 */
public final class Model implements Shape {

    private final String parent;
    private Slots textures = Slots.EMPTY;
    private Boolean ambientOcclusion;
    private String renderType;
    private String guiLight;
    private final List<Element> elements = new ArrayList<>();
    private boolean elementsStated;
    private final Map<String, JsonObject> display = new LinkedHashMap<>();
    private final List<JsonObject> overrides = new ArrayList<>();

    private Model(String parent) {
        this.parent = parent;
    }

    /** A model with no parent: its elements or its slot set are the whole model. */
    public static Model of() {
        return new Model(null);
    }

    /**
     * A model inheriting from {@code parent}. The parent is written exactly as given, so a bare
     * {@code block/block} stays bare; both spellings resolve to the same vanilla model.
     */
    public static Model parent(String parent) {
        if (parent == null || parent.isEmpty()) {
            throw new DejsonException("Model.parent() needs a parent; use Model.of() for none");
        }
        Models.requireId(parent.indexOf(':') < 0 ? "minecraft:" + parent : parent, "parent");
        return new Model(parent);
    }

    public Model textures(Slots textures) {
        this.textures = textures;
        return this;
    }

    public Model ambientOcclusion(boolean ambientOcclusion) {
        this.ambientOcclusion = ambientOcclusion;
        return this;
    }

    public Model renderType(String renderType) {
        Models.requireId(renderType, "render type");
        this.renderType = renderType;
        return this;
    }

    public Model guiLight(String guiLight) {
        if (!"side".equals(guiLight) && !"front".equals(guiLight)) {
            throw new DejsonException("guiLight takes \"side\" or \"front\", not " + guiLight);
        }
        this.guiLight = guiLight;
        return this;
    }

    /**
     * The model's cuboids. Called with none, it states an explicit empty list: a model that draws
     * nothing (a gas, whose look is particles) rather than one that inherits geometry.
     */
    public Model elements(Element... elements) {
        elementsStated = true;
        Collections.addAll(this.elements, elements);
        return this;
    }

    /** One raw {@code display} entry: the rotation, translation and scale vanilla applies in a slot. */
    public Model display(String slot, Number[] rotation, Number[] translation, Number[] scale) {
        if (display.containsKey(slot)) {
            throw new DejsonException("display slot '" + slot + "' declared twice");
        }
        JsonObject transform = new JsonObject();
        if (rotation != null) transform.add("rotation", triple(rotation, "rotation"));
        if (translation != null) transform.add("translation", triple(translation, "translation"));
        if (scale != null) transform.add("scale", triple(scale, "scale"));
        display.put(slot, transform);
        return this;
    }

    /**
     * One legacy item-model override: below 26.1 an item picks between models through these, where
     * 26.1 uses a {@code select} in the item definition.
     */
    public Model override(String model, String predicate, Number value) {
        Models.requireId(model, "override model");
        Models.requireId(predicate, "override predicate");
        JsonObject condition = new JsonObject();
        condition.addProperty(predicate, value);
        JsonObject override = new JsonObject();
        override.add("predicate", condition);
        override.addProperty("model", model);
        overrides.add(override);
        return this;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (parent != null) json.addProperty("parent", parent);
        if (ambientOcclusion != null) json.addProperty("ambientocclusion", ambientOcclusion);
        if (renderType != null) json.addProperty("render_type", renderType);
        if (guiLight != null) json.addProperty("gui_light", guiLight);
        if (!textures.isEmpty()) json.add("textures", textures.toJson());
        if (elementsStated) {
            JsonArray list = new JsonArray();
            for (Element element : elements) list.add(element.toJson());
            json.add("elements", list);
        }
        if (!display.isEmpty()) {
            JsonObject slots = new JsonObject();
            display.forEach((slot, transform) -> slots.add(slot, transform.deepCopy()));
            json.add("display", slots);
        }
        if (!overrides.isEmpty()) {
            JsonArray list = new JsonArray();
            for (JsonObject override : overrides) list.add(override.deepCopy());
            json.add("overrides", list);
        }
        return json;
    }

    @Override
    public Map<String, JsonObject> models(String base) {
        return Map.of(base, toJson());
    }

    private static JsonArray triple(Number[] values, String what) {
        if (values.length != 3) {
            throw new DejsonException("display " + what + " needs three values, not " + values.length);
        }
        return ItemModels.numberArray(values);
    }

    /** Three numbers, for {@link #display}. */
    public static Number[] xyz(Number x, Number y, Number z) {
        return new Number[] {x, y, z};
    }

    /** One cuboid of hand-built geometry. */
    public static final class Element {

        private final Number[] from;
        private final Number[] to;
        private String name;
        private JsonObject rotation;
        private Boolean shade;
        private Integer lightEmission;
        private final Map<String, Face> faces = new LinkedHashMap<>();

        private Element(Number[] from, Number[] to) {
            this.from = from;
            this.to = to;
        }

        public static Element box(Number fromX, Number fromY, Number fromZ, Number toX, Number toY, Number toZ) {
            return new Element(new Number[] {fromX, fromY, fromZ}, new Number[] {toX, toY, toZ});
        }

        /** The editor's label for the cuboid. Vanilla ignores it; it is kept so the model reads back the same. */
        public Element name(String name) {
            this.name = name;
            return this;
        }

        public Element rotation(Number originX, Number originY, Number originZ, String axis, Number angle) {
            return rotation(originX, originY, originZ, axis, angle, null);
        }

        public Element rotation(Number originX, Number originY, Number originZ, String axis, Number angle,
                                Boolean rescale) {
            if (!"x".equals(axis) && !"y".equals(axis) && !"z".equals(axis)) {
                throw new DejsonException("element rotation axis must be x, y or z, not " + axis);
            }
            JsonObject json = new JsonObject();
            json.add("origin", ItemModels.numberArray(originX, originY, originZ));
            json.addProperty("axis", axis);
            json.addProperty("angle", angle);
            if (rescale != null) json.addProperty("rescale", rescale);
            this.rotation = json;
            return this;
        }

        public Element shade(boolean shade) {
            this.shade = shade;
            return this;
        }

        public Element lightEmission(int lightEmission) {
            this.lightEmission = lightEmission;
            return this;
        }

        public Element face(Face face) {
            if (faces.putIfAbsent(face.direction, face) != null) {
                throw new DejsonException("element face '" + face.direction + "' declared twice");
            }
            return this;
        }

        public Element faces(Face... faces) {
            for (Face face : faces) face(face);
            return this;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            if (name != null) json.addProperty("name", name);
            json.add("from", ItemModels.numberArray(from));
            json.add("to", ItemModels.numberArray(to));
            if (rotation != null) json.add("rotation", rotation.deepCopy());
            if (shade != null) json.addProperty("shade", shade);
            if (lightEmission != null) json.addProperty("light_emission", lightEmission);
            JsonObject list = new JsonObject();
            faces.forEach((direction, face) -> list.add(direction, face.toJson()));
            json.add("faces", list);
            return json;
        }
    }

    /** One face of an {@link Element}: its texture slot, and optionally uv, cull side, rotation and tint. */
    public static final class Face {

        private static final java.util.Set<String> DIRECTIONS =
                java.util.Set.of("down", "up", "north", "south", "west", "east");

        private final String direction;
        private final String texture;
        private Number[] uv;
        private String cullface;
        private Integer rotation;
        private Integer tintIndex;

        private Face(String direction, String texture) {
            if (!DIRECTIONS.contains(direction)) {
                throw new DejsonException("'" + direction + "' is not a face direction");
            }
            if (texture == null || texture.isEmpty()) {
                throw new DejsonException("face " + direction + " names no texture");
            }
            this.direction = direction;
            this.texture = texture;
        }

        public static Face of(String direction, String texture) {
            return new Face(direction, texture);
        }

        public Face uv(Number u0, Number v0, Number u1, Number v1) {
            this.uv = new Number[] {u0, v0, u1, v1};
            return this;
        }

        public Face cullface(String cullface) {
            if (!DIRECTIONS.contains(cullface)) {
                throw new DejsonException("'" + cullface + "' is not a cull direction");
            }
            this.cullface = cullface;
            return this;
        }

        public Face rotation(int rotation) {
            Variant.requireQuadrant(rotation, "face");
            this.rotation = rotation;
            return this;
        }

        public Face tintIndex(int tintIndex) {
            this.tintIndex = tintIndex;
            return this;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            if (uv != null) json.add("uv", ItemModels.numberArray(uv));
            json.addProperty("texture", texture);
            if (cullface != null) json.addProperty("cullface", cullface);
            if (rotation != null) json.addProperty("rotation", rotation);
            if (tintIndex != null) json.addProperty("tintindex", tintIndex);
            return json;
        }
    }
}
