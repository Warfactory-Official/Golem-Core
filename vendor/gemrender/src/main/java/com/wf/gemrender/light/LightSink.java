package com.wf.gemrender.light;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

/**
 * One frame's lights. World coordinates; {@code intensity} scales {@code rgb} at 1 block before falloff
 * {@code (1 - (d/range)^4)^2 / (d^2 + 1)}.
 */
public interface LightSink {
    /**
     * @param shadow occluded by full opaque blocks within 64 blocks of the camera section. Shadowed light =>
     *               nothing lit past that window; source outside it, or beyond the nearest 16 shadowed => not shaded
     */
    void point(double x, double y, double z, float range, int rgb, float intensity, boolean shadow);

    /**
     * @param direction unit beam axis
     * @param up        any vector off the axis; orients the cookie
     * @param outerDegrees half angle, {@code < 89}
     * @param shadow       as {@link #point}
     */
    void spot(double x, double y, double z, Vector3fc direction, Vector3fc up, float range,
              float innerDegrees, float outerDegrees, int rgb, float intensity, @Nullable LightCookie cookie,
              boolean shadow);
}
