package dev.golem.core.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * The creature class a mob belongs to for Smite / Bane of Arthropods / healing inversion.
 *
 * <p>1.21 replaced the {@code MobType} enum with the entity-type tags {@code minecraft:undead},
 * {@code minecraft:arthropod} and their {@code sensitive_to_*} companions, so a modern mod declares
 * its class in a data JSON. 1.20.1 still asks {@code Mob.getMobType()}. The living facades answer that
 * hook from the same tags, so one JSON per mob serves every node: a datapack may define a tag under
 * any id, vanilla 1.20.1 simply never reads these two itself.
 */
public final class CreatureTypes {

    /** {@code minecraft:undead}: takes Smite, healed by harming and harmed by healing. */
    public static final TagKey<EntityType<?>> UNDEAD = tag("undead");
    /** {@code minecraft:arthropod}: takes Bane of Arthropods and its slowness. */
    public static final TagKey<EntityType<?>> ARTHROPOD = tag("arthropod");

    private CreatureTypes() {}

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, dev.golem.core.Ids.of("minecraft", name));
    }

    //? if <1.21.1 {
    /*// The 1.20.1 MobType for a mob, read from the tags; fallback when it is in neither.
    public static net.minecraft.world.entity.MobType mobType(LivingEntity entity,
                                                            net.minecraft.world.entity.MobType fallback) {
        if (entity.getType().is(UNDEAD)) return net.minecraft.world.entity.MobType.UNDEAD;
        if (entity.getType().is(ARTHROPOD)) return net.minecraft.world.entity.MobType.ARTHROPOD;
        return fallback;
    }
    *///?}
}
