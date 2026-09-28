package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The item-definition model tree, node by node.
 *
 * <pre>{@code
 * Dejson.item("hbm", "blueprints")
 *         .definition(ItemModels.select("hbm:item_variant")
 *                 .when("0", ItemModels.model("hbm:item/blueprints"))
 *                 .when("1", ItemModels.model("hbm:item/blueprints_1"))
 *                 .fallback(ItemModels.model("hbm:item/blueprints")))
 *         .register();
 * }</pre>
 *
 * <p>The vanilla node types are spelled out. A consumer's own node type ({@code hbm:machine_obj}, a
 * gun renderer) is a {@link #custom} node, which the consumer wraps in a vocabulary of its own: the
 * layer here does not know what fields another mod's renderer reads, only that they are plain values.
 */
public final class ItemModels {

    private ItemModels() {}

    /** {@code minecraft:model}: the model at {@code modelId}, with an optional positional tint list. */
    public static ItemModel model(String modelId, Tint... tints) {
        Models.requireId(modelId, "item model");
        return () -> {
            JsonObject model = new JsonObject();
            model.addProperty("type", "minecraft:model");
            model.addProperty("model", modelId);
            if (tints.length > 0) {
                JsonArray entries = new JsonArray();
                for (Tint tint : tints) {
                    entries.add(tint.toJson());
                }
                model.add("tints", entries);
            }
            return model;
        };
    }

    /**
     * {@code minecraft:special}: a renderer draws the item, and {@code base} supplies the particle
     * texture and the display transforms.
     */
    public static ItemModel special(String base, ItemModel renderer) {
        Models.requireId(base, "special base model");
        if (renderer == null) {
            throw new DejsonException("special(" + base + ") was given no renderer");
        }
        return () -> {
            JsonObject model = new JsonObject();
            model.addProperty("type", "minecraft:special");
            model.addProperty("base", base);
            model.add("model", renderer.toJson());
            return model;
        };
    }

    /** {@code minecraft:select} over {@code property}; add cases with {@link Select#when}. */
    public static Select select(String property) {
        Models.requireId(property, "select property");
        return new Select(property);
    }

    /**
     * {@code minecraft:condition} on the stack's custom model data flag at {@code index}: which of two
     * models an item shows depends on one boolean it carries.
     */
    public static ItemModel customModelDataFlag(int index, ItemModel onTrue, ItemModel onFalse) {
        if (index < 0) {
            throw new DejsonException("custom model data index cannot be negative: " + index);
        }
        if (onTrue == null || onFalse == null) {
            throw new DejsonException("a condition needs both branches");
        }
        return () -> {
            JsonObject model = new JsonObject();
            model.addProperty("type", "minecraft:condition");
            model.addProperty("property", "minecraft:custom_model_data");
            model.addProperty("index", index);
            model.add("on_true", onTrue.toJson());
            model.add("on_false", onFalse.toJson());
            return model;
        };
    }

    /** A node of a type the consumer's own renderer registers, built field by field. */
    public static Custom custom(String type) {
        Models.requireId(type, "item model type");
        return new Custom(type);
    }

    /** A {@code minecraft:select} under construction. Cases keep the order they were added in. */
    public static final class Select implements ItemModel {

        private final String property;
        private final JsonArray cases = new JsonArray();
        private ItemModel fallback;

        private Select(String property) {
            this.property = property;
        }

        public Select when(String value, ItemModel model) {
            return when(new JsonPrimitive(value), model);
        }

        /** One case matching any of several values, written as a JSON list. */
        public Select whenAny(ItemModel model, String... values) {
            if (values.length == 0) {
                throw new DejsonException("select " + property + " case lists no values");
            }
            JsonArray list = new JsonArray();
            for (String value : values) {
                list.add(value);
            }
            return when(list, model);
        }

        private Select when(JsonElement value, ItemModel model) {
            if (model == null) {
                throw new DejsonException("select " + property + " case " + value + " has no model");
            }
            JsonObject entry = new JsonObject();
            entry.add("when", value);
            entry.add("model", model.toJson());
            cases.add(entry);
            return this;
        }

        public Select fallback(ItemModel model) {
            this.fallback = model;
            return this;
        }

        @Override
        public JsonObject toJson() {
            if (cases.isEmpty()) {
                throw new DejsonException("select " + property + " has no cases");
            }
            JsonObject select = new JsonObject();
            select.addProperty("type", "minecraft:select");
            select.addProperty("property", property);
            select.add("cases", cases.deepCopy());
            if (fallback != null) {
                select.add("fallback", fallback.toJson());
            }
            return select;
        }
    }

    /**
     * A consumer-defined node: a type id and plain fields. Numbers keep the Java type they were given
     * in, so an {@code int} field stays an integer in the emitted file.
     */
    public static final class Custom implements ItemModel {

        private final JsonObject json = new JsonObject();

        private Custom(String type) {
            json.addProperty("type", type);
        }

        public Custom with(String key, String value) {
            return put(key, value == null ? JsonNull.INSTANCE : new JsonPrimitive(value));
        }

        public Custom with(String key, Number value) {
            return put(key, new JsonPrimitive(value));
        }

        public Custom with(String key, boolean value) {
            return put(key, new JsonPrimitive(value));
        }

        public Custom with(String key, ItemModel nested) {
            return put(key, nested.toJson());
        }

        /** A field holding a structure of the consumer's own; the consumer's vocabulary builds it. */
        public Custom with(String key, JsonElement value) {
            return put(key, value.deepCopy());
        }

        public Custom numbers(String key, Number... values) {
            return put(key, numberArray(values));
        }

        public Custom strings(String key, String... values) {
            JsonArray array = new JsonArray();
            for (String value : values) {
                array.add(value);
            }
            return put(key, array);
        }

        private Custom put(String key, JsonElement value) {
            if (key == null || key.isEmpty() || "type".equals(key)) {
                throw new DejsonException("custom item model field " + key + " is not settable");
            }
            if (json.has(key)) {
                throw new DejsonException("custom item model field '" + key + "' set twice");
            }
            json.add(key, value);
            return this;
        }

        @Override
        public JsonObject toJson() {
            return json.deepCopy();
        }
    }

    static JsonArray numberArray(Number... values) {
        JsonArray array = new JsonArray();
        for (Number value : values) {
            array.add(value);
        }
        return array;
    }
}
