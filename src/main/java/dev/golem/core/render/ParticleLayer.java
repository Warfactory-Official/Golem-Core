package dev.golem.core.render;

/**
 * How a {@link CompatParticle} wants its quad drawn.
 *
 * <p>This exists because the three eras express "draw a particle quad like this" in three unrelated
 * ways and none of them is a superset of the others:
 *
 * <ul>
 *   <li><b>26.1</b> — a particle returns a {@code SingleQuadParticle.Layer} record
 *       {@code (translucent, atlas, RenderPipeline)}. There is no per-particle GL state at all; the
 *       blend mode is baked into an immutable, pre-registered {@code RenderPipeline}.
 *   <li><b>1.21.1 / 1.20.1</b> — a particle returns a {@code ParticleRenderType} whose
 *       {@code begin(...)} sets raw GL state by hand, which is what the 1.12 originals did.
 * </ul>
 *
 * <p>So HBM code declares <em>intent</em> and each era resolves it natively. Keep this enum small:
 * every value costs a branch in {@link CompatParticle} for every supported era.
 */
public enum ParticleLayer {

    /**
     * Alpha-blended against the particle atlas, lit by the world. The default, and what most smoke,
     * debris and casing particles want.
     */
    TRANSLUCENT,

    /**
     * Alpha-blended but drawn fullbright, so the particle glows regardless of local light. Sparks,
     * flames and muzzle flashes use this.
     *
     * <p>Note this is <em>not</em> additive blending — see {@link #ADDITIVE}. Fullbright alone
     * reproduces the bulk of the 1.12 look, because the originals achieved "glow" mainly by
     * overriding {@code getBrightnessForRender} to return {@code 0xF000F0}.
     */
    EMISSIVE,

    /**
     * Additive blending, fullbright — the true "energy" look for balefire, plasma and shockwaves.
     *
     * <p>Resolved to a native additive particle layer by {@link CompatParticle}.
     */
    ADDITIVE;

    /** Whether this layer ignores world lighting and draws at full brightness. */
    public boolean isFullbright() {
        return this != TRANSLUCENT;
    }
}
