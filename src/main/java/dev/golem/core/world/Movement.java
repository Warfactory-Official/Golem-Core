package dev.golem.core.world;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Native entity movement state and player-flight hooks. */
public final class Movement {

    private Movement() {}

    public static Vec3 stuckSpeedMultiplier(Entity entity) {
        return entity.stuckSpeedMultiplier;
    }

    /** Changes only the multiplier; native makeStuckInBlock also resets fall distance. */
    public static void stuckSpeedMultiplier(Entity entity, Vec3 multiplier) {
        entity.stuckSpeedMultiplier = multiplier;
    }

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
