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

    /** Keeps moving entities' velocity packets enabled on loaders that expose that builder option. */
    public static <T extends Entity> EntityType.Builder<T> trackDeltas(EntityType.Builder<T> builder) {
        //? if neoforge || forge {
        return builder.setShouldReceiveVelocityUpdates(true);
        //?} else {
        /*return builder;
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
}
