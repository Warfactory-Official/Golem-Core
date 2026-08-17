package dev.golem.core.client;

import net.minecraft.client.Minecraft;

/**
 * How far the current frame sits between two ticks.
 *
 * <p>1.21 introduced {@code DeltaTracker} and split the value in two — one that pauses with the game and
 * one that does not; before that it was the single {@code Minecraft.getFrameTime()}. Anything
 * interpolating a render (a swaying weapon, a spinning part) wants the game-time flavour, which is what
 * this returns.
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
