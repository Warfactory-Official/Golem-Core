package dev.golem.core.world;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Cross-version construction of identifier-keyed attribute modifiers. */
public final class Attributes {

    private Attributes() {}

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
}
