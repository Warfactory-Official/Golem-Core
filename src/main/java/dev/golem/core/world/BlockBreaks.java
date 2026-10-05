package dev.golem.core.world;

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
 * <p>Notification only (no veto, no experience change). Forge-like loaders answer from {@code BlockEvent.BreakEvent}
 * (skipped when an earlier listener already cancelled the break), Fabric from {@code PlayerBlockBreakEvents.BEFORE}
 * (returning true, so the break goes ahead). {@link Harvests#fireBreakEvent} posts the same loader events, so a
 * harvest driven by a tool reaches these handlers too.
 */
public final class BlockBreaks {

    private BlockBreaks() {}

    @FunctionalInterface
    public interface Handler {
        void onBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state);
    }

    private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<>();
    private static boolean hooked;

    public static synchronized void onBreak(Handler handler) {
        HANDLERS.add(handler);
        if (hooked) return;
        hooked = true;
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) -> {
                    if (!event.isCanceled()) broken(event.getPlayer(), event.getLevel(), event.getPos(), event.getState());
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.level.BlockEvent.BreakEvent event) -> {
                    if (!event.isCanceled()) broken(event.getPlayer(), event.getLevel(), event.getPos(), event.getState());
                });
        *///?} else {
        /*net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            broken(player, level, pos, state);
            return true;
        });
        *///?}
    }

    /** Dispatches a break. Only server players in a server level reach the handlers, as in the legacy event. */
    public static void broken(Object player, Object level, BlockPos pos, BlockState state) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) return;
        for (Handler handler : HANDLERS) handler.onBreak(serverPlayer, serverLevel, pos, state);
    }
}
