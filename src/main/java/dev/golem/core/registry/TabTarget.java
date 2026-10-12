package dev.golem.core.registry;

import net.minecraft.world.item.Item;

/**
 * Somewhere a registration's item is listed, normally one of a mod's creative tabs ({@link Registrar.Handle#tab}).
 * An addon lists its items in another mod's tab through that mod's tab constants.
 */
@FunctionalInterface
public interface TabTarget {

    /** Lists {@code item} here, after everything listed before it. */
    void list(RegistryObject<? extends Item> item);
}
