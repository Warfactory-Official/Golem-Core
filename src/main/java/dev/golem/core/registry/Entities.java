package dev.golem.core.registry;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;

/** Entity-type construction, across the change in what {@code Builder.build} is given. */
public final class Entities {

    private Entities() {}

    /**
     * Finishes an {@link EntityType.Builder}.
     *
     * <p>26.1 builds against the registry key itself; older versions take the id's path as a bare
     * string (it is only used to derive the type's translation and loot-table ids). Passing the key on
     * every version means the call site never has to know which, and never has to cast.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity> EntityType<T> build(EntityType.Builder<T> builder, ResourceKey<?> key) {
        //? if >=26.1 {
        return builder.build((ResourceKey<EntityType<?>>) (ResourceKey) key);
        //?} else {
        /*return builder.build(dev.golem.core.Ids.keyId(key).getPath());
        *///?}
    }

    /** Disables the entity loot table across the builder API change. */
    public static <T extends Entity> EntityType.Builder<T> noLoot(EntityType.Builder<T> builder) {
        //? if >=26.1 {
        return builder.noLootTable();
        //?} else {
        /*return builder;
        *///?}
    }

    /** The builder's eye height (1.20.5+); 1.20.1 derives it from the hitbox and has no such option. */
    public static <T extends Entity> EntityType.Builder<T> eyeHeight(EntityType.Builder<T> builder, float eyeHeight) {
        //? if >=1.20.5 {
        return builder.eyeHeight(eyeHeight);
        //?} else {
        /*return builder;
        *///?}
    }

    /** Keeps moving entities' velocity packets enabled on loaders that expose that builder option. */
    public static <T extends Entity> EntityType.Builder<T> trackDeltas(EntityType.Builder<T> builder) {
        //? if neoforge || forge {
        return builder.setShouldReceiveVelocityUpdates(true);
        //?} else {
        /*return builder;
        *///?}
    }

    /**
     * Whether vanilla's {@code Entity.setXRot} clamps the value it stores to [-90, 90].
     *
     * <p>From 26.1 it does — {@code Math.clamp(xRot % 360.0F, -90.0F, 90.0F)}. Below it the field
     * takes whatever it is handed. So an entity that models a pitch outside that range, such as a
     * missile nosing over past vertical, needs its own copy of the value on 26.1 and does not below
     * it, and a test asserting that {@code getXRot} was clamped is asserting a fact about one era
     * rather than about the entity.
     */
    public static boolean clampsXRot() {
        //? if >=26.1 {
        return true;
        //?} else {
        /*return false;
        *///?}
    }

    /** Resolves an entity type by namespaced id across the registry lookup rename. */
    public static EntityType<?> byId(String id) {
        //? if >=26.1 {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(dev.golem.core.Ids.parse(id));
        //?} else {
        /*return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(dev.golem.core.Ids.parse(id));
        *///?}
    }

    /** Creates a mob for an entity conversion, preserving the modern creation reason where available. */
    public static <T extends Mob> T createForConversion(EntityType<T> type, ServerLevel level) {
        //? if >=26.1 {
        return type.create(level, net.minecraft.world.entity.EntitySpawnReason.CONVERSION);
        //?} else {
        /*return type.create(level);
        *///?}
    }

    /** Creates an entity with the explicit triggered spawn reason across the entity-construction API change. */
    public static <T extends Entity> T createTriggered(EntityType<T> type, ServerLevel level) {
        //? if >=26.1 {
        return type.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        //?} else {
        /*return type.create(level);
        *///?}
    }

    /**
     * Creates an entity with an explicit spawn reason where the constructor takes one (26.1+); older
     * versions only consumed the reason through {@code EntityType.spawn}, so {@code create(Level)} is used.
     */
    public static <T extends Entity> T create(EntityType<T> type, net.minecraft.world.level.Level level,
            //? if >=26.1 {
            net.minecraft.world.entity.EntitySpawnReason reason) {
        return type.create(level, reason);
        //?} else {
        /*net.minecraft.world.entity.MobSpawnType reason) {
        return type.create(level);
        *///?}
    }

    /**
     * Creates a throwaway entity on any level (a client preview that is never added to the world),
     * across the entity-construction API change.
     */
    public static <T extends Entity> T createDetached(EntityType<T> type, net.minecraft.world.level.Level level) {
        //? if >=26.1 {
        return type.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
        //?} else {
        /*return type.create(level);
        *///?}
    }

    /** Loads entity save data from a compound tag across the ValueInput rename. */
    public static void loadFromTag(Entity entity, net.minecraft.nbt.CompoundTag tag) {
        //? if >=26.1 {
        entity.load(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, entity.level().registryAccess(), tag));
        //?} else {
        /*entity.load(tag);
        *///?}
    }
}
