package dev.golem.core.registry;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

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
}
