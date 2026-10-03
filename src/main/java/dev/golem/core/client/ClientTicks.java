package dev.golem.core.client;

/**
 * A callback at the end of every client tick.
 *
 * <p>Each loader spells this differently and none of them agree on where it lives: fabric-api has a
 * dedicated lifecycle event, NeoForge splits the tick into {@code Pre}/{@code Post} subclasses on the
 * game bus, and Forge has one event carrying a {@code phase} field that fires twice. A consumer only
 * ever wants "after the client has ticked", so that is the whole surface here.
 *
 * <p>End rather than start deliberately: input state (key/mouse down flags) is settled by then, which
 * is what a per-tick input poll needs to read.
 *
 * <p>Client-only: never call this from common initialization.
 */
public final class ClientTicks {

    private ClientTicks() {}

    /** Runs {@code callback} after every client tick, for as long as the game runs. */
    public static void onEndTick(Runnable callback) {
        //? if fabric {
        /*net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                client -> callback.run());
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> callback.run());
        //?} elif forge {
        /*// One event for both halves of the tick here, so the phase has to be filtered by hand —
        // without this the callback runs twice per tick and every edge-detecting consumer breaks.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.ClientTickEvent event) -> {
                    if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) callback.run();
                });
        *///?}
    }

    /**
     * Runs {@code callback} before every client tick: after the input that arrived since the last tick has been
     * buffered (key clicks counted) and before {@code Minecraft.handleKeybinds} consumes it, so a consumer can take a
     * click away from vanilla (see {@link AttackInput}).
     */
    public static void onStartTick(Runnable callback) {
        //? if fabric {
        /*net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(
                client -> callback.run());
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) -> callback.run());
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.ClientTickEvent event) -> {
                    if (event.phase == net.minecraftforge.event.TickEvent.Phase.START) callback.run();
                });
        *///?}
    }
}
