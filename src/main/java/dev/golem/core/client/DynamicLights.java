package dev.golem.core.client;

import dev.golem.core.gemrender.GemLights;
import dev.golem.core.render.Instancing;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Point and spot lights shaded by GemRender 0.1.17+, polled on the render thread at the start of every level frame.
 * Without GemRender or under a shader pack {@link #shading} is false and nothing is polled: keep a fallback.
 */
public final class DynamicLights {

    /**
     * World coordinates; irradiance {@code rgb * intensity * (1 - (d/range)^4)^2 / (d^2 + softness^2)}, softness at
     * least 1. A shadowed light is occluded by full opaque blocks within 64 blocks of the camera.
     */
    public interface Sink {
        void point(double x, double y, double z, float range, int rgb, float intensity, boolean shadow);

        /** Half angles in degrees, outer below 89; {@code up} is any vector off the axis and turns the cookie. */
        void spot(double x, double y, double z, Vector3fc direction, Vector3fc up, float range, float innerDegrees,
                  float outerDegrees, int rgb, float intensity, @Nullable Cookie cookie, boolean shadow, float softness);
    }

    @FunctionalInterface
    public interface Provider {
        void collect(Sink sink, float partialTick);
    }

    /** A spot's projected texture: RGB (linear) multiplies the beam, the centre is the axis, the inscribed circle the outer cone. */
    public record Cookie(String namespace, String path) {}

    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static boolean bound;

    private DynamicLights() {}

    public static void register(Provider provider) {
        PROVIDERS.add(provider);
        if (!bound && supported()) {
            bound = true;
            GemLights.bind(PROVIDERS);
        }
    }

    /** Whether lights are shaded this frame; false means providers are not polled. */
    public static boolean shading() {
        return supported() && GemLights.shading();
    }

    private static boolean supported() {
        return Instancing.isAvailable() && GemLights.supported();
    }
}
