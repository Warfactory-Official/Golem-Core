package dev.golem.core.dejson;

import com.google.gson.JsonObject;

/**
 * One finished blockstate variant: a model id and the quarter turns applied to it.
 *
 * <p>Zero rotations and a false {@code uvlock} are omitted from the JSON rather than written as
 * defaults, which is what vanilla's own generator does and what every file on disk looks like.
 */
public record Variant(String model, int x, int y, boolean uvlock) {

    public Variant(String model) {
        this(model, 0, 0, false);
    }

    public Variant {
        Models.requireId(model, "variant model");
        requireQuadrant(x, "x");
        requireQuadrant(y, "y");
    }

    static void requireQuadrant(int degrees, String axis) {
        if (degrees != 0 && degrees != 90 && degrees != 180 && degrees != 270) {
            throw new DejsonException("rotation " + axis + "=" + degrees
                    + " is not a quarter turn; blockstate rotations are 0, 90, 180 or 270");
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("model", model);
        if (x != 0) {
            json.addProperty("x", x);
        }
        if (y != 0) {
            json.addProperty("y", y);
        }
        if (uvlock) {
            json.addProperty("uvlock", true);
        }
        return json;
    }
}
