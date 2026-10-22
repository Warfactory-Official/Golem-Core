package com.wf.gemrender.particle;

/** Bulk optics of billboard clouds drawn with {@link ParticleModels#absorbance}. */
public final class ParticleOptics {
    private static final int AGE_SAMPLES = 64;

    /** Texels below this are discarded by the absorbance material. */
    private static final float DISCARD = 1e-3f;

    private ParticleOptics() {
    }

    /**
     * Expected extinction per block along a ray through a steady-state cloud: {@code n E_u[w^2 mean_tex(tau)]},
     * {@code tau = -ln(1 - a t)}, unit age {@code u} uniform, {@code w} = {@link ParticleMotion#size}.
     *
     * @param perCubicBlock live particles per block^3
     * @param texelAlpha    sprite alpha, 0..1, every texel
     */
    public static float extinction(ParticleStyle style, float sizeScale, float perCubicBlock, float[] texelAlpha) {
        double sum = 0.0;
        for (int i = 0; i < AGE_SAMPLES; i++) {
            float u = (i + 0.5f) / AGE_SAMPLES;
            float a = ParticleMotion.alpha(style, u);
            float w = ParticleMotion.size(style, sizeScale, u);
            double tau = 0.0;
            for (float t : texelAlpha) {
                float at = a * t;
                if (at >= DISCARD) {
                    tau -= Math.log(Math.max(1.0f - at, 1e-4f));
                }
            }
            sum += w * w * tau / texelAlpha.length;
        }
        return (float) (perCubicBlock * sum / AGE_SAMPLES);
    }
}
