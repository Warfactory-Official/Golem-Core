package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/**
 * "A player tries to sleep in a bed" with one shape on every loader - the legacy {@code PlayerSleepInBedEvent}, which
 * fired at the start of every bed attempt, before the game decided whether the player may sleep.
 *
 * <p>Notification only (no veto). NeoForge answers from {@code CanPlayerSleepEvent}, Forge from
 * {@code PlayerSleepInBedEvent}, Fabric from {@code EntitySleepEvents.ALLOW_SLEEPING} (returning no problem, so the
 * vanilla decision stands). All three fire once per server-side bed attempt.
 */
public final class Sleeps {

    private Sleeps() {}

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final Hook<BiConsumer<Player, BlockPos>> ATTEMPTS = Hook.notifying();
    private static boolean hooked;

    /** Registers a handler for a server-side bed attempt: (player, bed position). */
    public static synchronized void onSleepAttempt(BiConsumer<Player, BlockPos> handler) {
        ATTEMPTS.register(handler);
        if (hooked) return;
        hooked = true;
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent event) ->
                        attempted(event.getEntity(), event.getPos()));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerSleepInBedEvent event) ->
                        attempted(event.getEntity(), event.getPos()));
        *///?} else {
        /*net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> {
            attempted(player, pos);
            return null;
        });
        *///?}
    }

    /** Dispatches a bed attempt. Client-side calls and attempts without a position are ignored. */
    public static void attempted(Player player, BlockPos pos) {
        if (pos == null || player.level().isClientSide()) return;
        for (BiConsumer<Player, BlockPos> handler : ATTEMPTS) handler.accept(player, pos);
    }
}
