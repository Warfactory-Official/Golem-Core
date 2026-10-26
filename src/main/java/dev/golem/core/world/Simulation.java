package dev.golem.core.world;

import net.minecraft.server.MinecraftServer;

/** Server simulation state whose accessor changed across the supported range. */
public final class Simulation {

    private Simulation() {}

    /**
     * Whether simulation runs normally (not frozen by tick-rate control). Below 1.20.5 there is no freeze, so this
     * is always true.
     */
    public static boolean runsNormally(MinecraftServer server) {
        //? if >=1.20.5 {
        return server.tickRateManager().runsNormally();
        //?} else {
        /*return true;
        *///?}
    }
}
