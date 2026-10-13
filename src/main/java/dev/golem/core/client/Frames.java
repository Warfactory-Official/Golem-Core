package dev.golem.core.client;

import net.minecraft.client.Minecraft;

/**
 * How far the current frame sits between two ticks.
 *
 * <p>Returns the game-time flavour (the one that pauses with the game), which is what anything
 * interpolating a render wants. 1.21 split it from the real-time value in {@code DeltaTracker}; before that
 * there was the single {@code Minecraft.getFrameTime()}.
 *
 * <p>Client-only.
 */
public final class Frames {

    private Frames() {}

    /** The partial tick for interpolating game state this frame. */
    public static float partialTick() {
        //? if >=26.1 {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        //?} elif >=1.21 {
        /*// 1.21 introduced DeltaTracker but still reached it through getTimer(); only the accessor
        // was renamed later.
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        *///?} else {
        /*return Minecraft.getInstance().getFrameTime();
        *///?}
    }
}
