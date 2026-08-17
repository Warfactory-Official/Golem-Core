package dev.golem.core.registry;

import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;

/**
 * Furnace burn times, registered the same way on every loader and version.
 *
 * <p>The shape of this changed twice and in opposite directions. NeoForge reads burn times from a
 * {@code neoforge:furnace_fuels} data map — plain JSON, no code at all. Fabric registered them into a
 * global mutable {@code FuelRegistry} until 26.1, which replaced it with a {@code FuelValues} builder
 * assembled per-server. A consumer holding a map of item to ticks should not have to care which.
 *
 * <p>On NeoForge and Forge this is a no-op by design: the data map (or the {@code getBurnTime}
 * override) already carries the values, and registering them twice would be the bug.
 */
public final class Fuels {

    private Fuels() {}

    /**
     * Registers every entry as a furnace fuel. Call from the loader's common initializer, after
     * registration has flushed so the suppliers resolve.
     */
    public static void register(Map<? extends Supplier<? extends Item>, Integer> burnTimes) {
        //? if fabric && >=26.1 {
        /*// 26.1 builds fuel values per server, so the map is replayed into each builder as it is made.
        net.fabricmc.fabric.api.registry.FuelValueEvents.BUILD.register((builder, context) ->
                burnTimes.forEach((item, ticks) -> builder.add(item.get(), ticks)));
        *///?} elif fabric {
        /*// Pre-26.1 fabric-api keeps one global mutable registry.
        burnTimes.forEach((item, ticks) -> net.fabricmc.fabric.api.registry.FuelRegistry.INSTANCE.add(item.get(), ticks));
        *///?}
        // Forge-like loaders: nothing to do — see the class note.
    }
}
