package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "A player is about to break a block" with one shape on every loader - the legacy {@code BlockEvent.BreakEvent},
 * fired on the server before the block is removed.
 *
 * <p>Checks may veto a break before notification listeners run. Forge-like loaders answer from
 * {@code BlockEvent.BreakEvent}, Fabric from {@code PlayerBlockBreakEvents.BEFORE}. A break already cancelled by
 * another listener reaches neither. {@link Harvests#fireBreakEvent} posts the same loader events, so a harvest
 * driven by a tool reaches these handlers too.
 */
public final class BlockBreaks {

    private BlockBreaks() {}

    @FunctionalInterface
    public interface Handler {
        void onBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state);
    }

    @FunctionalInterface
    public interface Before {
        boolean beforeBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state);
    }

    private static final Hook<Before> CHECKS = Hook.of(listeners -> (player, level, pos, state) -> {
        for (var listener : listeners) if (!listener.beforeBreak(player, level, pos, state)) return false;
        return true;
    });

    /** A check returning false cancels removal and prevents break notifications. */
    public static synchronized void onBeforeBreak(Before check) {
        CHECKS.register(check);
        hook();
    }

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final Hook<Handler> HANDLERS = Hook.notifying();
    private static boolean hooked;

    public static synchronized void onBreak(Handler handler) {
        HANDLERS.register(handler);
        hook();
    }

    private static synchronized void hook() {
        if (hooked) return;
        hooked = true;
        //? if neoforge && >=26.1 {
        // NeoForge 26.1.2 moved BlockEvent.BreakEvent to level.block.BreakBlockEvent (same members)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.block.BreakBlockEvent event) -> {
                    if (!event.isCanceled() && !before(event.getPlayer(), event.getLevel(), event.getPos(), event.getState())) {
                        event.setCanceled(true);
                    }
                });
        //?} elif neoforge {
        /*net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) -> {
                    if (!event.isCanceled() && !before(event.getPlayer(), event.getLevel(), event.getPos(), event.getState())) {
                        event.setCanceled(true);
                    }
                });
        *///?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.level.BlockEvent.BreakEvent event) -> {
                    if (!event.isCanceled() && !before(event.getPlayer(), event.getLevel(), event.getPos(), event.getState())) {
                        event.setCanceled(true);
                    }
                });
        *///?} else {
        /*net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            return before(player, level, pos, state);
        });
        *///?}
    }

    private static boolean before(Object player, Object level, BlockPos pos, BlockState state) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) return true;
        if (!CHECKS.invoker().beforeBreak(serverPlayer, serverLevel, pos, state)) return false;
        broken(serverPlayer, serverLevel, pos, state);
        return true;
    }

    /** Dispatches a break. Only server players in a server level reach the handlers, as in the legacy event. */
    public static void broken(Object player, Object level, BlockPos pos, BlockState state) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) return;
        for (Handler handler : HANDLERS) handler.onBreak(serverPlayer, serverLevel, pos, state);
    }
}
