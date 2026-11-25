package dev.golem.core.render;

/**
 * How {@link CompatParticle#keepFaintFragments()} particles draw while an Iris or Oculus shader pack is in use.
 *
 * <p>The faint layers ({@link FaintParticleLayers}) bind Core's own {@code golemcore:particle_faint} program. A shader
 * pack replaces the vanilla particle program with its own gbuffer program, but it cannot know Core's, so a faint particle
 * drew with a program that writes nowhere the pack composites: cooling-tower steam, haze and radiation fog were simply
 * invisible. Under a pack, a faint particle therefore returns vanilla's translucent particle type (or Core's additive
 * layer, which keeps the engine's particle program), and the pack's own program draws it.
 *
 * <p>That program keeps the vanilla alpha test (Iris discards fragments at alpha 0.1 and below), which hides exactly the
 * fragments the faint layer exists for. {@link #alpha} keeps a fading particle's average coverage instead: below the
 * floor each particle is drawn at the floor with probability {@code alpha / floor}, stable per particle, so a fading
 * plume thins out puff by puff rather than vanishing at once or turning denser.
 */
final class FaintShaderFallback {

    /** Just above Iris's {@code ONE_TENTH_ALPHA} test, which keeps only fragments whose alpha is greater than 0.1. */
    static final float SHADER_ALPHA_FLOOR = 0.105F;

    private FaintShaderFallback() {}

    //? if >=1.21.11 {
    /** The layer a faint particle draws with under a shader pack, or {@code null} to keep Core's faint layer. */
    static net.minecraft.client.particle.SingleQuadParticle.Layer layer(boolean faint, ParticleLayer layer,
            net.minecraft.client.particle.SingleQuadParticle.Layer additive) {
        if (!faint || !dev.golem.core.client.ShaderPacks.inUse()) return null;
        return layer == ParticleLayer.ADDITIVE ? additive : net.minecraft.client.particle.SingleQuadParticle.Layer.TRANSLUCENT;
    }
    //?} else {
    /*/^* The render type a faint particle draws with under a shader pack, or {@code null} to keep Core's faint type. ^/
    static net.minecraft.client.particle.ParticleRenderType layer(boolean faint, ParticleLayer layer,
            net.minecraft.client.particle.ParticleRenderType additive) {
        if (!faint || !dev.golem.core.client.ShaderPacks.inUse()) return null;
        return layer == ParticleLayer.ADDITIVE ? additive
                : net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
    *///?}

    /** The alpha a faint particle's vertices carry: unchanged without a shader pack, or at and above the floor. */
    static float alpha(float alpha, Object particle, boolean faint) {
        if (!faint || alpha <= 0F || alpha >= SHADER_ALPHA_FLOOR || !dev.golem.core.client.ShaderPacks.inUse()) {
            return alpha;
        }
        int hash = System.identityHashCode(particle) * 0x9E3779B9;
        float keep = ((hash >>> 8) & 0xFFFF) / 65536F;
        return keep * SHADER_ALPHA_FLOOR < alpha ? SHADER_ALPHA_FLOOR : 0F;
    }
}
