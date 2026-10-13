package dev.golem.core.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Client decision seam for a player-specific camera offset. */
public final class CameraHooks {

    private CameraHooks() {}

    @FunctionalInterface
    public interface Hook {
        boolean lowerThirdPersonCamera(Player player);
    }

    /**
     * Every {@link Hook} listener, in phase order: the third-person camera is lowered when any of them asks for it (HBM:
     * the active glider). A list, not a slot: a second mod's listener joins the first's instead of replacing it.
     */
    public static final dev.golem.core.hook.Hook<Hook> LOWER_THIRD_PERSON = dev.golem.core.hook.Hook.notifying();

    public static boolean lowerThirdPersonCamera(Player player) {
        if (player == null || LOWER_THIRD_PERSON.isEmpty()) return false;
        for (Hook listener : LOWER_THIRD_PERSON) if (listener.lowerThirdPersonCamera(player)) return true;
        return false;
    }

    /**
     * How far behind (or, mirrored, in front of) the player a detached camera wants to sit: receives vanilla's distance
     * (4 blocks, scaled by the entity's size from 1.21 on, its {@code camera_distance} attribute on 26.1) and returns the
     * one to use. The camera still pulls in where a block is in the way, as vanilla's. 1.7 mods set
     * {@code EntityRenderer.thirdPersonDistance} for this (hbm-space: 12 blocks while riding a rocket).
     */
    @FunctionalInterface
    public interface Distance {
        float thirdPersonDistance(Player player, float distance);
    }

    /** Every {@link Distance} listener, in phase order; each receives the previous one's answer. */
    public static final dev.golem.core.hook.Hook<Distance> DISTANCE = dev.golem.core.hook.Hook.notifying();

    /** The detached camera distance after every {@link #DISTANCE} listener (vanilla's when there are none). */
    public static float thirdPersonDistance(Entity entity, float distance) {
        if (!(entity instanceof Player player) || DISTANCE.isEmpty()) return distance;
        for (Distance listener : DISTANCE) distance = listener.thirdPersonDistance(player, distance);
        return distance;
    }
}
