package dev.golem.core.render;

//? if >=26.1 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * The render types block-entity renderers draw with.
 *
 * <p>26.1 moved these factories out of {@code RenderType} into a separate {@code RenderTypes} holder
 * in the {@code ...renderer.rendertype} package. This is a naming shim kept as real code because a
 * substring rename of {@code RenderType} would also mangle every symbol that contains it.
 */
public final class CompatRenderTypes {

    private CompatRenderTypes() {}


    public static RenderType entitySolid(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        //? if >=26.1 {
        return RenderTypes.entitySolid(texture);
        //?} else {
        /*return RenderType.entitySolid(texture);
        *///?}
    }

    /**
     * Alpha-tested entity geometry, <b>culled on every node</b>: the old GL default state a 1.7 renderer drew
     * in unless it disabled {@code GL_CULL_FACE} itself. Identical to {@link #entityCutoutCull}.
     *
     * <p>Vanilla 26.1's {@code RenderTypes.entityCutout} is the <i>no-cull</i> pipeline while every older
     * {@code RenderType.entityCutout} culls; mapping the name straight through would draw back faces on 26.1
     * only. A caller whose 1.7 renderer drew double-sided (every {@code RenderLiving}, or one that disabled
     * culling) names {@link #entityCutoutNoCull}.
     */
    public static RenderType entityCutout(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        //? if >=26.1 {
        return RenderTypes.entityCutoutCull(texture);
        //?} else {
        /*return RenderType.entityCutout(texture);
        *///?}
    }

    /** Alpha-tested entity geometry with face culling enabled, matching the old GL default. */
    public static RenderType entityCutoutCull(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        //? if >=26.1 {
        return RenderTypes.entityCutoutCull(texture);
        //?} else {
        /*return RenderType.entityCutout(texture);
        *///?}
    }

    /** An alpha-tested entity pass with face culling disabled, for legacy double-sided OBJ geometry. */
    public static RenderType entityCutoutNoCull(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
        texture) {
        //? if >=26.1 {
        // 26.1's entityCutout is the no-cull pipeline; entityCutoutCull is the opt-in culling variant.
        return RenderTypes.entityCutout(texture);
        //?} else {
        /*return RenderType.entityCutoutNoCull(texture);
        *///?}
    }

    public static RenderType entityTranslucent(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        //? if >=26.1 {
        return RenderTypes.entityTranslucent(texture);
        //?} else {
        /*return RenderType.entityTranslucent(texture);
        *///?}
    }

    /**
     * {@link #entityTranslucent} with back faces culled: a 1.7 translucent renderer that left {@code GL_CULL_FACE}
     * on. Pre-26.1 this is vanilla's {@code entityTranslucentCull}; 26.1 only keeps the item-target variant, so the
     * same {@code ENTITY_TRANSLUCENT_CULL} pipeline is set up here drawing into the main target.
     */
    public static RenderType entityTranslucentCull(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        //? if >=26.1 {
        return ENTITY_TRANSLUCENT_CULL.apply(texture);
        //?} else {
        /*return RenderType.entityTranslucentCull(texture);
        *///?}
    }

    //? if >=26.1 {
    private static final java.util.function.Function<Identifier, RenderType> ENTITY_TRANSLUCENT_CULL =
            net.minecraft.util.Util.memoize(texture -> RenderType.create("golem_entity_translucent_cull",
                    net.minecraft.client.renderer.rendertype.RenderSetup
                            .builder(net.minecraft.client.renderer.RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                            .withTexture("Sampler0", texture)
                            .useLightmap()
                            .useOverlay()
                            .affectsCrumbling()
                            .sortOnUpload()
                            .setOutline(net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                            .createRenderSetup()));
    //?}

    /** The vanilla line pipeline, moved alongside the other render-type factories in 26.1. */
    public static RenderType lines() {
        //? if >=26.1 {
        return RenderTypes.lines();
        //?} else {
        /*return RenderType.lines();
        *///?}
    }

    public static RenderType entityTranslucentEmissive(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        //? if >=26.1 {
        return RenderTypes.entityTranslucentEmissive(texture);
        //?} else {
        /*return RenderType.entityTranslucentEmissive(texture);
        *///?}
    }

    /**
     * A self-lit glow drawn over solid geometry, alpha-weighted additive, depth-tested but not
     * depth-writing, unculled. See {@link PlasmaGlowRenderType} for why no stock type fits.
     */
    public static RenderType plasmaGlow(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        return PlasmaGlowRenderType.of(texture);
    }

    /** {@link #plasmaGlow} with no depth test either: drawn over everything. */
    public static RenderType plasmaGlowSeeThrough(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        return PlasmaGlowRenderType.ofSeeThrough(texture);
    }

    /** Torex's legacy fog-free, lightmapped {@code SRC_ALPHA/ONE} flare with no depth write. */
    public static RenderType torexFlare(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        return TorexFlareRenderType.of(texture);
    }

    public record FlareVertex(float red, float green, float blue, float alpha, int packedLight) {}

    /** Adapts the old flare row to the render type available on this target. */
    public static FlareVertex torexFlareVertex(float red, float green, float blue,
                                                float alpha, int packedLight) {
        //? if >=26.1 {
        return new FlareVertex(red, green, blue, alpha, packedLight);
        //?} else {
        /*return new FlareVertex(red * alpha, green * alpha, blue * alpha, 1F, 0x00F000F0);
        *///?}
    }

    /** Translucent variant with explicit outline control; both eras expose the same two-arg form. */
    public static RenderType entityTranslucent(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture, boolean outline) {
        //? if >=26.1 {
        return RenderTypes.entityTranslucent(texture, outline);
        //?} else {
        /*return RenderType.entityTranslucent(texture, outline);
        *///?}
    }

    public static RenderType energySwirl(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture, float u, float v) {
        //? if >=26.1 {
        return RenderTypes.energySwirl(texture, u, v);
        //?} else {
        /*return RenderType.energySwirl(texture, u, v);
        *///?}
    }

    /**
     * Vanilla 26.1's dragon rays: POSITION_COLOR <b>triangles</b>, additive, no depth write, and <b>culled</b>. For an
     * old {@code glDisable(GL_CULL_FACE)} additive pass use {@link #additive()} / {@link #additiveTriangles()}.
     */
    public static RenderType dragonRays() {
        //? if >=26.1 {
        return RenderTypes.dragonRays();
        //?} else {
        /*return AdditiveRenderType.dragonRays();
        *///?}
    }

    /**
     * Old untextured additive geometry ({@code GL_SRC_ALPHA, GL_ONE}, depth mask off, face culling off) as
     * POSITION_COLOR <b>quads</b>, identical on every node. Emit each face once: both sides are drawn.
     */
    public static RenderType additive() {
        return AdditiveRenderType.quads();
    }

    /** {@link #additive()} for geometry emitted as POSITION_COLOR <b>triangles</b>. */
    public static RenderType additiveTriangles() {
        return AdditiveRenderType.triangles();
    }

    /**
     * {@link #additive()} that still writes depth: an old renderer that disabled culling and blended additively but
     * never called {@code glDepthMask(false)}, so its near faces hide the far faces of the same glow.
     */
    public static RenderType additiveDepthWrite() {
        return AdditiveRenderType.quadsDepth();
    }

    /** Textureless alpha-weighted additive sheets, matching vanilla's lightning pipeline. */
    public static RenderType lightning() {
        return LightningRenderType.get();
    }

    /** Textureless, translucent, depth-tested smoke sheets with no depth writes. */
    public static RenderType weaponSmoke() {
        return WeaponSmokeRenderType.get();
    }

    /**
     * Alpha-tested, unculled, lightmapped geometry with no cardinal lighting, a 1.7 renderer's
     * {@code glDisable(GL_LIGHTING)} sheet. Emit an upward normal; see {@link FlatCutoutRenderType}
     * for why the stock cutout darkens the back side of a double-sided sheet on 26.1.
     */
    public static RenderType flatCutout(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        return FlatCutoutRenderType.of(texture);
    }

    /**
     * {@link #flatCutout} blended {@code SRC_ALPHA / ONE_MINUS_SRC_ALPHA}, back faces culled, depth written: a 1.12
     * translucent sheet drawn with {@code GL_LIGHTING} off. Fade it with the vertex colour's alpha.
     */
    public static RenderType flatTranslucentCull(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        return FlatCutoutRenderType.translucentCull(texture);
    }

    /**
     * {@link #flatCutout} blended {@code SRC_ALPHA / ONE_MINUS_SRC_ALPHA}, unculled, depth tested without writing it: a
     * 1.12 particle or decal drawn with {@code GL_LIGHTING} off and {@code depthMask(false)}. Emit an upward normal.
     * Only alpha 0 is discarded, so a faint particle (1.12's {@code alphaFunc(GL_GREATER, 1/255)}) stays visible.
     */
    public static RenderType flatTranslucent(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        return FlatCutoutRenderType.translucent(texture);
    }

    /** {@link #flatCutout}, depth-biased toward the camera: a decal a hair in front of a surface. */
    public static RenderType flatCutoutDecal(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        return FlatCutoutRenderType.decal(texture);
    }

    /**
     * The item glint over a texture and UVs of the caller's choosing; {@code additive} picks {@code ONE, ONE}
     * over the glint's {@code SRC_COLOR, ONE}. See {@link GlintRenderType}: depth {@code EQUAL}, so submit it
     * after (a higher order than) the surface it coats, with {@link Vertexes#positionTex}.
     */
    public static RenderType glint(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture, boolean additive) {
        return GlintRenderType.of(texture, additive);
    }
}
