package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * "An entity placed a block" with one shape on every loader - the legacy {@code BlockEvent.PlaceEvent}, fired on the
 * server after the block is in the world (and after its {@code setPlacedBy}).
 *
 * <p>Notification only: a handler that wants the block gone or different changes the world itself (an atmosphere mod
 * that snuffs a torch placed in vacuum swaps the block). Forge-like loaders answer from
 * {@code BlockEvent.EntityPlaceEvent} (every block of a multi-block placement too, skipped when an earlier listener
 * cancelled it). Fabric, which has no place event, reports block-item placements and flint-and-steel or fire-charge
 * fire through {@code MixinBlockItemPlaced}, {@code MixinFlintAndSteelPlaced} and {@code MixinFireChargePlaced}; a
 * campfire or candle those items light is reported on Forge-like loaders only.
 */
public final class BlockPlaces {

    private BlockPlaces() {}

    @FunctionalInterface
    public interface Handler {
        /** {@code placer} may be null (a dispenser on Fabric, a non-entity cause on Forge-like loaders). */
        void onPlace(@Nullable Entity placer, ServerLevel level, BlockPos pos, BlockState placed);
    }

    /** Every listener, in phase order; register through {@link #onPlace} (which hooks the loader once) or directly. */
    public static final Hook<Handler> HANDLERS = Hook.notifying();
    private static boolean hooked;

    public static synchronized void onPlace(Handler handler) {
        HANDLERS.register(handler);
        if (hooked) return;
        hooked = true;
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) -> {
                    if (!event.isCanceled()) placed(event.getEntity(), event.getLevel(), event.getPos(), event.getPlacedBlock());
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) -> {
                    if (!event.isCanceled()) placed(event.getEntity(), event.getLevel(), event.getPos(), event.getPlacedBlock());
                });
        *///?}
    }

    /** Dispatches a placement. Only server levels reach the handlers, as in the legacy event. */
    public static void placed(@Nullable Entity placer, Object level, BlockPos pos, BlockState state) {
        if (HANDLERS.isEmpty() || !(level instanceof ServerLevel serverLevel)) return;
        for (Handler handler : HANDLERS) handler.onPlace(placer, serverLevel, pos, state);
    }
}
