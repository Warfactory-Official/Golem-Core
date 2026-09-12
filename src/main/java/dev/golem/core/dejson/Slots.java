package dev.golem.core.dejson;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The texture slots of one generated model, in declaration order.
 *
 * <p><b>Slots are never pruned.</b> A texture entry that no face in the parent chain references is
 * not dead weight — it can be the slot carrier a Java model baker or a connected-texture engine
 * reads. Dropping one fails the bake with "Missing texture references" instead of merely drawing an
 * untextured face, and the report names the model rather than whatever deleted the entry. Anything
 * put here is emitted.
 *
 * <p>A value is either a sprite id ({@code hbm:blocks/steel}, or a bare path resolved against
 * {@code minecraft}) or a reference to another slot ({@code #side}). Both are validated here so a
 * typo throws at the registration site.
 */
public final class Slots {

    private static final Pattern SLOT = Pattern.compile("[a-z0-9_]+");
    private static final Pattern SPRITE = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");

    public static final Slots EMPTY = new Slots(Collections.emptyMap());

    private final Map<String, String> values;

    private Slots(Map<String, String> values) {
        this.values = values;
    }

    public static Slots of(String slot, String texture) {
        return EMPTY.and(slot, texture);
    }

    public static Slots of(String slot, String texture, String slot2, String texture2) {
        return of(slot, texture).and(slot2, texture2);
    }

    public static Slots of(String slot, String texture, String slot2, String texture2,
                           String slot3, String texture3) {
        return of(slot, texture, slot2, texture2).and(slot3, texture3);
    }

    public Slots and(String slot, String texture) {
        if (slot == null || !SLOT.matcher(slot).matches()) {
            throw new DejsonException("texture slot name " + quote(slot)
                    + " is not a lowercase identifier");
        }
        if (texture == null) {
            throw new DejsonException("texture for slot '" + slot + "' is null");
        }
        String reference = texture.startsWith("#") ? texture.substring(1) : texture;
        Pattern shape = texture.startsWith("#") ? SLOT : SPRITE;
        if (!shape.matcher(reference).matches()) {
            throw new DejsonException("texture " + quote(texture) + " for slot '" + slot
                    + "' is neither a sprite id nor a #slot reference");
        }
        if (values.containsKey(slot)) {
            throw new DejsonException("texture slot '" + slot + "' declared twice");
        }
        Map<String, String> merged = new LinkedHashMap<>(values);
        merged.put(slot, texture);
        return new Slots(Collections.unmodifiableMap(merged));
    }

    /** The slot values in declaration order. */
    public Map<String, String> values() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        values.forEach(json::addProperty);
        return json;
    }

    /**
     * The sprite ids this declaration names, with {@code #slot} references excluded. The delivery
     * layer feeds these to the resource manager once it has one, which is the only point at which
     * "does this texture exist" can be answered.
     */
    public java.util.Set<String> sprites() {
        java.util.Set<String> sprites = new java.util.LinkedHashSet<>();
        for (String value : values.values()) {
            if (!value.startsWith("#")) {
                sprites.add(value);
            }
        }
        return sprites;
    }

    private static String quote(String value) {
        return value == null ? "null" : "'" + value + "'";
    }
}
