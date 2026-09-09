package dev.golem.core.client;

/** The render partial tick, whose owner changed when 26.1 introduced DeltaTracker. */
public final class FrameTime {

    private FrameTime() {}

    public static float partialTick() {
        return Frames.partialTick();
    }
}
