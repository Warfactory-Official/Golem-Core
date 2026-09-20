package dev.golem.core.render;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Alpha-tested, unculled, lightmapped entity geometry with <b>no cardinal lighting</b>: the vertex colour
 * times the lightmap, and nothing else — what a 1.7 renderer drew after {@code glDisable(GL_LIGHTING)}.
 *
 * <p>Why the stock cutout cannot stand in for it on 26.1: {@code RenderPipelines.ENTITY_CUTOUT} defines
 * {@code PER_FACE_LIGHTING}, under which {@code entity.vsh} lights the vertex twice — once from its normal
 * and once from the <i>negated</i> normal — and {@code entity.fsh} picks by {@code gl_FrontFacing}. A
 * double-sided sheet (a hanging wire, a ribbon, a flag) is a front face from one side and a back face
 * from the other, so with the pipeline unculled its far side always lands on the ambient floor of
 * {@code 0.4}. No normal a caller could emit fixes that; the shader has to stop consulting it.
 *
 * <p>Below 26.1 the entity shader has no front/back split — {@code minecraft_mix_light} is evaluated once
 * per vertex from the emitted normal — so the stock no-cull cutout is used and the caller's normal decides
 * the shade. An <b>upward</b> normal {@code (0, 1, 0)} evaluates to exactly {@code 1.0} there (both level
 * light directions lean up: {@code 2 × 0.8165 × 0.6 + 0.4 > 1}, clamped), which is the flat result. Callers
 * emit that normal unconditionally; 26.1 ignores it.
 */
public final class FlatCutoutRenderType {

    private FlatCutoutRenderType() {}

    //? if >=26.1 {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation("pipeline/golem_flat_cutout")
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    private static final Function<Identifier, RenderType> BY_TEXTURE = Util.memoize(texture ->
            RenderType.create("golem_flat_cutout", RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .affectsCrumbling()
                    .createRenderSetup()));

    public static RenderType of(Identifier texture) {
        return BY_TEXTURE.apply(texture);
    }
    //?} else {
    /*public static RenderType of(ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }
    *///?}
}
