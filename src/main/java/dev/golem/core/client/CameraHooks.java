package dev.golem.core.client;

import net.minecraft.world.entity.player.Player;

/** Client decision seam for a player-specific camera offset. */
public final class CameraHooks {

    private CameraHooks() {}

    @FunctionalInterface
    public interface Hook {
        boolean lowerThirdPersonCamera(Player player);
    }

    private static Hook hook = player -> false;

    public static void register(Hook hook) {
        CameraHooks.hook = hook == null ? player -> false : hook;
    }

    public static boolean lowerThirdPersonCamera(Player player) {
        return player != null && hook.lowerThirdPersonCamera(player);
    }
}
