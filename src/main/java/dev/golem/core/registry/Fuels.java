package dev.golem.core.registry;

import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;

/**
 * Furnace burn times, registered the same way on every loader and version.
 *
 * <p>The loaders differ. NeoForge reads burn times from a {@code neoforge:furnace_fuels} data map: plain
 * JSON, no code at all. Fabric below 26.1 registers them into a global mutable {@code FuelRegistry}; 26.1
 * uses a {@code FuelValues} builder assembled per server. A consumer holding a map of item to ticks does
 * not see which.
 *
 * <p>On NeoForge this is a no-op by design: the {@code neoforge:furnace_fuels} data map already carries
 * the values, and registering them twice would be the bug. Forge 1.20.1 has no fuel data map; there the
 * values answer Forge's {@code FurnaceFuelBurnTimeEvent}, which every furnace-like consumer of
 * {@code ForgeHooks.getBurnTime} fires, exactly where an item's own {@code getBurnTime} would have.
 */
public final class Fuels {

    private Fuels() {}

    /**
     * Registers every entry as a furnace fuel. Call from the loader's common initializer, after
     * registration has flushed so the suppliers resolve.
     */
    public static void register(Map<? extends Supplier<? extends Item>, Integer> burnTimes) {
        //? if >=26.3 {
        /*// 26.3 fuel is the item's cooking_fuel component (NeoForge's fuel data map is gone with it).
        REGISTERED.add(burnTimes.keySet());
        //? if neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.event.ModifyDefaultComponentsEvent event) -> burnTimes.forEach((item, ticks) ->
                        event.modify(item.get(), (components, context, key) ->
                                components.set(net.minecraft.core.component.DataComponents.COOKING_FUEL, fuel(ticks)))));
        //?} else {
        /^net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents.MODIFY.register(context -> burnTimes.forEach((item, ticks) ->
                context.modify(item.get(), components ->
                        components.set(net.minecraft.core.component.DataComponents.COOKING_FUEL, fuel(ticks)))));
        ^///?}
        *///?} elif fabric && >=26.1 {
        /*// 26.1 builds fuel values per server, so the map is replayed into each builder as it is made.
        net.fabricmc.fabric.api.registry.FuelValueEvents.BUILD.register((builder, context) ->
                burnTimes.forEach((item, ticks) -> builder.add(item.get(), ticks)));
        *///?} elif fabric && >=1.21.11 {
        /*net.fabricmc.fabric.api.registry.FuelRegistryEvents.BUILD.register((builder, context) ->
                burnTimes.forEach((item, ticks) -> builder.add(item.get(), ticks)));
        *///?} elif fabric {
        /*burnTimes.forEach((item, ticks) -> net.fabricmc.fabric.api.registry.FuelRegistry.INSTANCE.add(item.get(), ticks));
        *///?}
        //? if forge {
        /*java.util.concurrent.atomic.AtomicReference<Map<Item, Integer>> resolved =
                new java.util.concurrent.atomic.AtomicReference<>();
        // Resolved once, on the first event (registries are frozen by then): the event fires for every
        // burn-time query of every stack, so it must not walk the whole table each time.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent event) -> {
                    Map<Item, Integer> table = resolved.get();
                    if (table == null) {
                        // later entries for the same item win, as the per-event walk they replace did
                        Map<Item, Integer> built = new java.util.IdentityHashMap<>();
                        burnTimes.forEach((item, ticks) -> built.put(item.get(), ticks));
                        resolved.compareAndSet(null, built);
                        table = resolved.get();
                    }
                    Integer ticks = table.get(event.getItemStack().getItem());
                    if (ticks != null) event.setBurnTime(ticks);
                });
        *///?}
        // NeoForge before 26.3: nothing to do. See the class note.
    }

    //? if >=26.3 {
    /*private static final java.util.List<java.util.Collection<? extends Supplier<? extends Item>>> REGISTERED =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /^*
     * A registered fuel's burn time in {@code furnace}: halved in smokers and blast furnaces, as their block entities did
     * before 26.3 moved that halving into vanilla's own fuel providers.
     ^/
    public static int burnDuration(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace,
                                   net.minecraft.world.item.ItemStack fuel, int ticks) {
        if (!(furnace instanceof net.minecraft.world.level.block.entity.SmokerBlockEntity)
                && !(furnace instanceof net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity)) return ticks;
        for (var items : REGISTERED) {
            for (Supplier<? extends Item> item : items) if (fuel.is(item.get())) return ticks / 2;
        }
        return ticks;
    }

    /^* A fixed burn time; {@link #burnDuration} halves it in smokers and blast furnaces. ^/
    private static net.minecraft.world.item.component.CookingFuel fuel(int ticks) {
        return new net.minecraft.world.item.component.CookingFuel(
                new net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt.Constant(ticks),
                net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat.fromKey(
                        net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER));
    }
    *///?}
}
