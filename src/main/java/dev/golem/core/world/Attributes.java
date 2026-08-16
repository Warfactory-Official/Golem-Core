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
}
