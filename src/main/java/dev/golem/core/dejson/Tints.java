package dev.golem.core.dejson;

import com.google.gson.JsonObject;

/** The tint sources an item definition can name, in the order the model's layers take them. */
public final class Tints {

    private Tints() {}

    /**
     * A fixed colour. {@code -1} is the untinted white a plain layer wants, and is what a two-layer
     * item puts in slot zero so only the overlay takes the stack's colour.
     */
    public static Tint constant(int argb) {
        return () -> {
            JsonObject tint = new JsonObject();
            tint.addProperty("type", "minecraft:constant");
            tint.addProperty("value", argb);
            return tint;
        };
    }

    /**
     * The colour the stack carries in its custom model data at {@code index}, or {@code fallback}
     * when it carries none. The fallback is RGB, not ARGB: vanilla supplies the alpha.
     */
    public static Tint customModelData(int index, int fallback) {
        if (index < 0) {
            throw new DejsonException("custom model data index cannot be negative: " + index);
        }
        return () -> {
            JsonObject tint = new JsonObject();
            tint.addProperty("type", "minecraft:custom_model_data");
            tint.addProperty("index", index);
            tint.addProperty("default", fallback);
            return tint;
        };
    }
}
