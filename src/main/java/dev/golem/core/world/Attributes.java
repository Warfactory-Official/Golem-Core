package dev.golem.core.world;

import dev.golem.core.Ids;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Cross-version construction of identifier-keyed attribute modifiers, and the upper bound of vanilla ranged
 * attributes.
 */
public final class Attributes {

    private Attributes() {}

    /**
     * Raises the upper bound of a vanilla {@link RangedAttribute} (for example {@code Attributes.MAX_HEALTH}, 1024,
     * or {@code Attributes.ATTACK_DAMAGE}, 2048, on 1.20.1, 1.21.1 and 26.1) to at least {@code max}.
     *
     * <p>Pre-1.16 content was written against {@code SharedMonsterAttributes}, whose bounds were effectively
     * unlimited; modern {@code RangedAttribute.sanitizeValue} clamps every computed value to {@code maxValue}, so a
     * 20,000-health boss is silently a 1,024-health one. The bound is one {@code final} field, made writable by
     * Core's access widener ({@code accessible} + {@code mutable}) and access transformers ({@code public-f}); no
     * mixin, reflection or bytecode is involved.
     *
     * <p>Idempotent and monotonic: a bound that is already at least {@code max} (another mod may have raised it
     * further) is left alone. Returns the bound in force.
     *
     * <p><b>When to call:</b> once, from common mod initialisation (the NeoForge/Forge mod constructor, Fabric's
     * {@code ModInitializer}), on both physical sides, before any entity exists: an {@link AttributeInstance}
     * caches the sanitised value in {@code getValue} and keeps a stale clamped one until its next modifier or
     * base-value change. The attribute objects are JVM-wide singletons, so in singleplayer one call covers both
     * the integrated server and the client.
     *
     * @throws IllegalArgumentException if the attribute is not a {@link RangedAttribute}, or {@code max} is NaN or
     *                                  below the attribute's minimum
     */
    public static double raiseMax(Holder<Attribute> attribute, double max) {
        return raiseMax(attribute.value(), max);
    }

    /** As {@link #raiseMax(Holder, double)}, for an attribute object (1.20.1's {@code Attributes} fields). */
    public static double raiseMax(Attribute attribute, double max) {
        if (!(attribute instanceof RangedAttribute ranged)) {
            throw new IllegalArgumentException("not a ranged attribute: " + attribute);
        }
        // Forge constructs mods on parallel workers; serialise concurrent raises from different mods.
        synchronized (Attributes.class) {
            if (Double.isNaN(max) || max < ranged.getMinValue()) {
                throw new IllegalArgumentException("bound " + max + " below minimum " + ranged.getMinValue()
                        + " of " + attribute.getDescriptionId());
            }
            if (ranged.getMaxValue() < max) ranged.maxValue = max;
            return ranged.getMaxValue();
        }
    }

    public static void replacePermanent(AttributeInstance attribute, String id, double amount) {
        //? if >=1.21 {
        var identifier = dev.golem.core.Ids.parse(id);
        attribute.removeModifier(identifier);
        attribute.addPermanentModifier(new AttributeModifier(identifier, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        //?} else {
        /*java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(id
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        attribute.removeModifier(uuid);
        attribute.addPermanentModifier(new AttributeModifier(uuid, id, amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        *///?}
    }

    /**
     * As {@link #replacePermanent}, but scaling the attribute's <b>base</b> value rather than its
     * running total - legacy operation {@code 1}.
     *
     * <p>The two agree while a modifier is alone on the attribute and diverge as soon as it is not,
     * so a value transcribed from a pre-1.16 source wants this one.
     */
    public static void replacePermanentBase(AttributeInstance attribute, String id, double amount) {
        if (attribute == null) return;
        //? if >=1.21 {
        var identifier = dev.golem.core.Ids.parse(id);
        attribute.removeModifier(identifier);
        attribute.addPermanentModifier(new AttributeModifier(identifier, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        //?} else {
        /*java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(id
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        attribute.removeModifier(uuid);
        attribute.addPermanentModifier(new AttributeModifier(uuid, id, amount,
                AttributeModifier.Operation.MULTIPLY_BASE));
        *///?}
    }

    /** Replaces a non-persistent modifier, preserving the operation used by the newest API. */
    public static void replaceTransient(AttributeInstance attribute, String id, double amount) {
        if (attribute == null) return;
        //? if >=1.21 {
        var identifier = dev.golem.core.Ids.parse(id);
        attribute.removeModifier(identifier);
        attribute.addOrUpdateTransientModifier(new AttributeModifier(identifier, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        //?} else {
        /*java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(id
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        attribute.removeModifier(uuid);
        attribute.addTransientModifier(new AttributeModifier(uuid, id, amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        *///?}
    }

    public static void removeTransient(AttributeInstance attribute, String id) {
        if (attribute == null) return;
        //? if >=1.21 {
        attribute.removeModifier(dev.golem.core.Ids.parse(id));
        //?} else {
        /*attribute.removeModifier(java.util.UUID.nameUUIDFromBytes(id
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        *///?}
    }

    /** The three legacy modifier operations, in their legacy order 0, 1, 2. */
    public enum Op { ADD, MULTIPLY_BASE, MULTIPLY_TOTAL }

    /**
     * Sets a non-persistent modifier with an explicit legacy operation. Leaves an identical modifier
     * alone, so a caller may re-assert it every tick without marking the attribute dirty (and so
     * without resyncing it to the client every tick).
     */
    public static void setTransient(AttributeInstance attribute, String id, double amount, Op op) {
        if (attribute == null || matches(attribute, id, amount, op)) return;
        //? if >=1.21 {
        var identifier = dev.golem.core.Ids.parse(id);
        attribute.removeModifier(identifier);
        attribute.addTransientModifier(new AttributeModifier(identifier, amount, operation(op)));
        //?} else {
        /*java.util.UUID uuid = uuid(id);
        attribute.removeModifier(uuid);
        attribute.addTransientModifier(new AttributeModifier(uuid, id, amount, operation(op)));
        *///?}
    }

    /** As {@link #setTransient}, but the modifier is saved with the entity. */
    public static void setPermanent(AttributeInstance attribute, String id, double amount, Op op) {
        if (attribute == null || matches(attribute, id, amount, op)) return;
        //? if >=1.21 {
        var identifier = dev.golem.core.Ids.parse(id);
        attribute.removeModifier(identifier);
        attribute.addPermanentModifier(new AttributeModifier(identifier, amount, operation(op)));
        //?} else {
        /*java.util.UUID uuid = uuid(id);
        attribute.removeModifier(uuid);
        attribute.addPermanentModifier(new AttributeModifier(uuid, id, amount, operation(op)));
        *///?}
    }

    /** Whether a modifier with this id is present on the attribute. */
    public static boolean has(AttributeInstance attribute, String id) {
        if (attribute == null) return false;
        //? if >=1.21 {
        return attribute.getModifier(dev.golem.core.Ids.parse(id)) != null;
        //?} else {
        /*return attribute.getModifier(uuid(id)) != null;
        *///?}
    }

    /** The amount of an existing modifier; callers establish presence with {@link #has}. */
    public static double modifierAmount(AttributeInstance attribute, String id) {
        //? if >=1.21 {
        return attribute.getModifier(Ids.parse(id)).amount();
        //?} else {
        /*return attribute.getModifier(uuid(id)).getAmount();
        *///?}
    }

    private static boolean matches(AttributeInstance attribute, String id, double amount, Op op) {
        //? if >=1.21 {
        AttributeModifier present = attribute.getModifier(dev.golem.core.Ids.parse(id));
        return present != null && present.amount() == amount && present.operation() == operation(op);
        //?} else {
        /*AttributeModifier present = attribute.getModifier(uuid(id));
        return present != null && present.getAmount() == amount && present.getOperation() == operation(op);
        *///?}
    }

    private static AttributeModifier.Operation operation(Op op) {
        //? if >=1.21 {
        return switch (op) {
            case ADD -> AttributeModifier.Operation.ADD_VALUE;
            case MULTIPLY_BASE -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case MULTIPLY_TOTAL -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        };
        //?} else {
        /*return switch (op) {
            case ADD -> AttributeModifier.Operation.ADDITION;
            case MULTIPLY_BASE -> AttributeModifier.Operation.MULTIPLY_BASE;
            case MULTIPLY_TOTAL -> AttributeModifier.Operation.MULTIPLY_TOTAL;
        };
        *///?}
    }

    //? if <1.21 {
    /*private static java.util.UUID uuid(String id) {
        return java.util.UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    *///?}
}
