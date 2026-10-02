package dev.golem.core.render.gem;

import dev.golem.core.Ids;
import dev.golem.core.render.CompatRenderTypes;
import java.util.Objects;

//? if >=26.1 {
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/**
 * What a {@linkplain Gem.Part part} is drawn with: one texture and one blending rule.
 *
 * <p>The two backends want that pair in different currencies — the vanilla pass wants a
 * {@code RenderType}, the instanced backend wants a material plus a texture id — so the declaration
 * states the rule and each backend converts. Textures are passed as strings and parsed through
 * {@link Ids}, the same shape {@code Billboards.Layer} uses, so this class needs no version-split in
 * its public API.
 *
 * <p>The {@code RenderType} is resolved lazily rather than in the factory. Vanilla's render-type
 * factories reach into pipeline state that a dedicated server has no business touching, and a
 * declaration is inspected headlessly by the coverage tests; deferring the lookup to the first draw
 * keeps those tests able to read a model without a client.
 */
public final class Surface {

    /**
     * How the surface blends.
     *
     * <p>{@link #SOLID} maps to the instanced backend's cutout material, which has an alpha test the
     * vanilla solid type lacks. That is only ever a difference for a texture with partially
     * transparent texels, which a solid surface is asserting it does not have.
     */
    public enum Blend {
        SOLID,
        CUTOUT,
        CUTOUT_CULL,
        CUTOUT_NO_CULL,
        TRANSLUCENT,
        TRANSLUCENT_EMISSIVE,
        PLASMA_GLOW
    }

    private final String texture;
    private final Blend blend;
    private RenderType renderType;

    private Surface(String texture, Blend blend) {
        if (texture == null || texture.isBlank()) throw new IllegalArgumentException("Blank surface texture");
        this.texture = texture;
        this.blend = Objects.requireNonNull(blend, "blend");
    }

    /**
     * Alpha-tested, culled as the old GL default did. The usual choice for a machine. Same pipeline as
     * {@link #cutoutCull} on every node; see {@link #renderType}.
     */
    public static Surface cutout(String texture) {
        return new Surface(texture, Blend.CUTOUT);
    }

    public static Surface cutoutCull(String texture) {
        return new Surface(texture, Blend.CUTOUT_CULL);
    }

    /** Alpha-tested and drawn from both sides, for thin geometry authored as a single sheet. */
    public static Surface cutoutNoCull(String texture) {
        return new Surface(texture, Blend.CUTOUT_NO_CULL);
    }

    public static Surface solid(String texture) {
        return new Surface(texture, Blend.SOLID);
    }

    /**
     * Blended, and deliberately not the outline-affecting translucent type.
     *
     * <p>Vanilla's one-argument factory asks for {@code AFFECTS_OUTLINE}, which is only ever read while
     * something is drawing into the glowing-outline target — nothing a block entity or its instanced
     * twin ever does. Asking for it anyway would buy a second buffer per texture and a second place for
     * draw order to differ between the two backends.
     */
    public static Surface translucent(String texture) {
        return new Surface(texture, Blend.TRANSLUCENT);
    }

    /**
     * Blended, lit by nothing, and not depth-writing — molten metal and glowing rods, which upstream
     * drew through vanilla's {@code entityTranslucentEmissive}.
     */
    public static Surface emissive(String texture) {
        return new Surface(texture, Blend.TRANSLUCENT_EMISSIVE);
    }

    /**
     * Alpha-weighted additive over solid geometry, depth-tested but not depth-writing and unculled —
     * 1.7's {@code SRC_ALPHA/ONE} glow. See {@code PlasmaGlowRenderType} for why no stock type fits.
     */
    public static Surface glow(String texture) {
        return new Surface(texture, Blend.PLASMA_GLOW);
    }

    public String texture() {
        return texture;
    }

    public Blend blend() {
        return blend;
    }

    /** The vanilla render type for this surface, resolved on first use and kept. */
    public RenderType renderType() {
        RenderType resolved = renderType;
        if (resolved == null) {
            resolved = switch (blend) {
                case SOLID -> CompatRenderTypes.entitySolid(Ids.parse(texture));
                // Culled on every node. 26.1's entityCutout is the NO-cull pipeline, while every older
                // entityCutout and both instanced backends cull, so naming it here made a CUTOUT surface draw
                // its back faces on 26.1 only (icon audit 2026-10-01: difurnace_ext's floor, the colored duct's
                // far end, armor interiors). The old GL default the factory documents is culled.
                case CUTOUT -> CompatRenderTypes.entityCutoutCull(Ids.parse(texture));
                case CUTOUT_CULL -> CompatRenderTypes.entityCutoutCull(Ids.parse(texture));
                case CUTOUT_NO_CULL -> CompatRenderTypes.entityCutoutNoCull(Ids.parse(texture));
                case TRANSLUCENT -> CompatRenderTypes.entityTranslucent(Ids.parse(texture), false);
                case TRANSLUCENT_EMISSIVE -> CompatRenderTypes.entityTranslucentEmissive(Ids.parse(texture));
                case PLASMA_GLOW -> CompatRenderTypes.plasmaGlow(Ids.parse(texture));
            };
            renderType = resolved;
        }
        return resolved;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Surface surface && blend == surface.blend && texture.equals(surface.texture);
    }

    @Override
    public int hashCode() {
        return texture.hashCode() * 31 + blend.hashCode();
    }

    @Override
    public String toString() {
        return blend + "(" + texture + ")";
    }
}
