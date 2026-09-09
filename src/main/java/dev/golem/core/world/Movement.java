package dev.golem.core.world;

import net.minecraft.server.level.ServerPlayer;

/** Common player-flight movement hooks without leaking loader/version internals into gameplay code. */
public final class Movement {

    private Movement() {}

    /** Resets vanilla's anti-floating counter on every loader/version target. */
    public static void resetFloatingTickCount(ServerPlayer player) {
        if (player == null) return;
        //? if >=26.1 {
        player.connection.resetFlyingTicks();
        //?} else {
        /*if (player.connection instanceof FloatingTickCounter counter) counter.resetFloatingTickCount();
        *///?}
    }
}
