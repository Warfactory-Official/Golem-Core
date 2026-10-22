package com.wf.gemrender.light;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dynamic point and spot lights, forward-shaded in terrain (vanilla + Sodium), entity, particle,
 * Flywheel and direct-path shaders. Shadows per light ({@link LightSink#point}).
 *
 * <p>Off while an Iris shader pack is in use.
 */
public final class Lights {
    /**
     * Lights shaded per frame after culling; the nearest win.
     */
    public static final int MAX = 64;

    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private Lights() {
    }

    /**
     * Polled on the render thread at the start of every level frame.
     */
    public static void register(Provider provider) {
        PROVIDERS.add(provider);
    }

    public static void unregister(Provider provider) {
        PROVIDERS.remove(provider);
    }

    /** False => providers not polled, nothing shaded (shader pack): keep a fallback. */
    public static boolean shading() {
        return !LightShaders.standDown();
    }

    /**
     * Any thread. Texture resampled to {@link LightCookies#SIZE}^2; reloaded with resources.
     */
    public static LightCookie cookie(ResourceLocation texture) {
        return LightCookies.cookie(texture);
    }

    static List<Provider> providers() {
        return PROVIDERS;
    }

    @FunctionalInterface
    public interface Provider {
        void collect(LightSink sink, float partialTick);
    }
}
