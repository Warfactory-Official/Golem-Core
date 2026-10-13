package dev.golem.core.render;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;
//?} else {
/*import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;

import java.util.function.Function;
*///?}

/**
 * The item glint over a texture of the caller's choosing, with the caller's own UVs: the glint shader, depth
 * test {@code EQUAL} with no depth write, no culling, and no texture transform.
 *
 * <p>Vanilla binds its own {@code enchanted_glint_item} sheet to every glint type, animates it with a fixed
 * texture matrix, and its glint shader reads no vertex colour. A 1.7 renderer that tinted its glint with
 * {@code glColor} and scrolled it with its own matrix therefore needs all three freed: the colour goes into the
 * sheet (the blend multiplies by the source, so a pre-multiplied sheet is the tinted sheet) and the scrolling
 * into the caller's UVs.
 *
 * <p>Two blends. {@code additive = false} is vanilla's and 1.7's 3D glint, {@code SRC_COLOR, ONE}; {@code true}
 * is {@code ONE, ONE}, which is 1.7's flat inventory glint ({@code DST_ALPHA, ONE} over an opaque slot).
 *
 * <p>Like vanilla's, it only shows where something was drawn at the same depth first: submit it after the
 * surface it coats (a higher {@link SubmitSink#order}), over the same vertices. Vertex format
 * {@code POSITION_TEX}; write it with {@link Vertexes#positionTex}.
 */
public final class GlintRenderType {

    private GlintRenderType() {}

    //? if >=26.1 {
    private static final RenderPipeline ADDITIVE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation("pipeline/golem_glint_additive")
            .withVertexShader("core/glint")
            .withFragmentShader("core/glint")
            .withSampler("Sampler0")
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
            .withDepthStencilState(RenderPipelines.GLINT.getDepthStencilState())
            .build();

    private static final Function<Identifier, RenderType> GLINT = Util.memoize(texture ->
            RenderType.create("golem_glint", RenderSetup.builder(RenderPipelines.GLINT)
                    .withTexture("Sampler0", texture).createRenderSetup()));

    private static final Function<Identifier, RenderType> ADDITIVE_GLINT = Util.memoize(texture ->
            RenderType.create("golem_glint_additive", RenderSetup.builder(ADDITIVE)
                    .withTexture("Sampler0", texture).createRenderSetup()));

    public static RenderType of(Identifier texture, boolean additive) {
        return (additive ? ADDITIVE_GLINT : GLINT).apply(texture);
    }
    //?} else {
    /*/^* {@code create} and the state shards are protected on {@code RenderType}, hence the subclass. ^/
    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        static RenderType glint(ResourceLocation texture, boolean additive) {
            return create(additive ? "golem_glint_additive" : "golem_glint", DefaultVertexFormat.POSITION_TEX,
                    VertexFormat.Mode.QUADS, 256, false, false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_GLINT_SHADER)
                            .setTextureState(new TextureStateShard(texture, true, false))
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .setDepthTestState(EQUAL_DEPTH_TEST)
                            .setTransparencyState(additive ? ADDITIVE_TRANSPARENCY : GLINT_TRANSPARENCY)
                            .createCompositeState(false));
        }
    }

    private static final Function<ResourceLocation, RenderType> GLINT = Util.memoize(texture -> Factory.glint(texture, false));
    private static final Function<ResourceLocation, RenderType> ADDITIVE_GLINT = Util.memoize(texture -> Factory.glint(texture, true));

    public static RenderType of(ResourceLocation texture, boolean additive) {
        return (additive ? ADDITIVE_GLINT : GLINT).apply(texture);
    }
    *///?}
}
