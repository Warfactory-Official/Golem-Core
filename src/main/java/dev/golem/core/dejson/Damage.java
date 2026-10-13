package dev.golem.core.dejson;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Damage types declared in code, together with the vanilla tags they belong to.
 *
 * <p>The type lives under {@code data/<ns>/damage_type/} and its tag membership under
 * {@code data/minecraft/tags/damage_type/}. Nothing connects the two files, and a forgotten tag has no
 * symptom at the point of the mistake (armour quietly protects against radiation).
 *
 * <p>{@link #declare} takes every type at once and writes both halves from one table. It is one call
 * because each tag file is assembled from every type that named it.
 *
 * <pre>{@code
 * Damage.declare("hbm",
 *         Damage.type("radiation").tags(Damage.BYPASSES_ARMOR),
 *         Damage.type("bullet_fire").projectile().tags(Damage.IS_FIRE));
 * }</pre>
 */
public final class Damage {

    /** Scaling: the damage is never scaled by difficulty. */
    public static final String NEVER = "never";
    /** Scaling: scaled by difficulty when a non-player living entity caused it. */
    public static final String BY_LIVING_NON_PLAYER = "when_caused_by_living_non_player";
    /** Scaling: always scaled by difficulty. */
    public static final String ALWAYS = "always";

    public static final String BYPASSES_ARMOR = "minecraft:bypasses_armor";
    public static final String BYPASSES_EFFECTS = "minecraft:bypasses_effects";
    public static final String BYPASSES_INVULNERABILITY = "minecraft:bypasses_invulnerability";
    public static final String IS_EXPLOSION = "minecraft:is_explosion";
    public static final String IS_FIRE = "minecraft:is_fire";
    public static final String IS_PROJECTILE = "minecraft:is_projectile";

    private Damage() {}

    /** Begins a damage type. Its death message key and exhaustion default to the quiet case. */
    public static Type type(String path) {
        return new Type(path);
    }

    /**
     * Writes every type and the tag files they imply.
     *
     * <p>A tag is emitted only if some type named it, with members in declaration order.
     */
    public static void declare(String namespace, Type... types) {
        if (namespace == null || namespace.isEmpty()) {
            throw new DejsonException("damage types need a namespace");
        }
        if (types == null || types.length == 0) {
            throw new DejsonException("declare(" + namespace + ") was given no damage types");
        }
        Map<String, List<String>> members = new LinkedHashMap<>();
        for (Type type : types) {
            JsonObject json = new JsonObject();
            json.addProperty("exhaustion", type.exhaustion);
            json.addProperty("message_id", type.message == null ? type.path : type.message);
            json.addProperty("scaling", type.scaling);
            Data.declare("data/" + namespace + "/damage_type/" + type.path + ".json", json);
            for (String tag : type.tags) {
                members.computeIfAbsent(tag, key -> new ArrayList<>()).add(namespace + ":" + type.path);
            }
        }
        members.forEach((tag, ids) -> {
            int colon = tag.indexOf(':');
            Tags.damageType(tag.substring(0, colon), tag.substring(colon + 1), ids.toArray(new String[0]));
        });
    }

    /** One damage type: what it is called, how much it tires you out, and what it counts as. */
    public static final class Type {

        private final String path;
        private final Set<String> tags = new LinkedHashSet<>();

        private String message;
        private double exhaustion;
        private String scaling = NEVER;

        private Type(String path) {
            if (path == null || path.isEmpty()) {
                throw new DejsonException("a damage type needs a path");
            }
            this.path = path;
        }

        /**
         * The death-message key, where it is not the type's own name.
         *
         * <p>Two types can share one (a piercing and an ordinary bullet), differing only in armour handling.
         */
        public Type message(String message) {
            this.message = message;
            return this;
        }

        public Type exhaustion(double exhaustion) {
            this.exhaustion = exhaustion;
            return this;
        }

        public Type scaling(String scaling) {
            if (!NEVER.equals(scaling) && !BY_LIVING_NON_PLAYER.equals(scaling) && !ALWAYS.equals(scaling)) {
                throw new DejsonException("'" + scaling + "' is not a damage scaling rule");
            }
            this.scaling = scaling;
            return this;
        }

        /** The shorthand for something thrown or fired: it tires, it scales, and it is a projectile. */
        public Type projectile() {
            return exhaustion(0.1).scaling(BY_LIVING_NON_PLAYER).tags(IS_PROJECTILE);
        }

        public Type tags(String... names) {
            for (String name : names) {
                Models.requireId(name, "damage type tag");
                if (!tags.add(name)) {
                    throw new DejsonException(path + " is put in " + name + " twice");
                }
            }
            return this;
        }
    }
}
