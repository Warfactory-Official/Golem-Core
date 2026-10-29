package dev.golem.core.harness;

import net.minecraft.server.MinecraftServer;

/** Development harness clocks; ordinary clients and servers retain vanilla pacing until explicitly requested. */
public final class HarnessPacing {

    private static volatile long serverMillis = 50L;
    private static volatile float clientMillis;

    private HarnessPacing() {
    }

    public static long serverMillis() {
        return serverMillis;
    }

    public static float clientMillis(float original) {
        var requested = clientMillis;
        return requested == 0F ? original : requested;
    }

    public static int frameLimit(int original) {
        var requested = clientMillis;
        // Vanilla processes at most ten client ticks per frame.
        return requested == 0F ? original : Math.max(original, (int) Math.ceil(1000F / (requested * 10F)) + 1);
    }

    static float serverRate(MinecraftServer server, float rate) {
        //? if >=1.21 {
        var manager = server.tickRateManager();
        var previous = manager.tickrate();
        manager.setTickRate(rate);
        return previous;
        //?} else {
        /*var millis = 1000F / rate;
        if (millis != (long) millis)
            throw new IllegalArgumentException("1.20.1 requires a whole number of milliseconds per tick: " + rate);
        var previous = 1000F / serverMillis;
        serverMillis = (long) millis;
        return previous;
        *///?}
    }

    static float serverRate(MinecraftServer server) {
        //? if >=1.21 {
        return server.tickRateManager().tickrate();
        //?} else {
        /*return 1000F / serverMillis;
         *///?}
    }

    static void clientRate(float rate) {
        clientMillis = 1000F / rate;
    }

    static void serverStarted(MinecraftServer server) {
        var requested = clientMillis;
        if (requested != 0F) serverRate(server, 1000F / requested);
    }
}
