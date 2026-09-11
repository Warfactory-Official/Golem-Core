package dev.golem.core.flywheel.indirect;

//? if neoforge && >=26.1 {

import dev.golem.core.GolemCore;
import dev.golem.core.flywheel.Material;
import dev.golem.core.flywheel.Pipelines;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

// INDIRECT-side pipelines. Same vertex format / depth / blend / cull config as the matching
// INSTANCING pipeline, but without the "Instances" UBO declaration — INDIRECT binds an SSBO
// raw via glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, group.ssbo) instead. The shader pair
// (golem_indirect.vsh / .fsh) reads ntmInstances[gl_BaseInstanceARB + gl_InstanceID] from that
// SSBO.
//
// CRUMBLING is omitted: the assembly-machine crumbling pass remains on the INSTANCING path
// for now (slice 2c-3 ships the opaque/translucent draw only). When INDIRECT adds crumbling,
// we'll add the matching CRUMBLING pipeline here.
public final class IndirectPipelines {

    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "core/golem_indirect");
    private static final Identifier CRUMBLING_SHADER = Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "core/golem_indirect_crumbling");
    private static final Identifier DEPTH_ONLY_SHADER = Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "core/golem_indirect_depth");

    private static final RenderPipeline[] PIPELINES = new RenderPipeline[Material.VALUES.length];
    // Depth-only variants for sortable translucent materials. Used by IndirectDrawManager's
    // Fabulous-mode second multidraw to write closest-particle depth into the translucent
    // layer's depth attachment (so the transparency post-chain sorts our layer correctly
    // against clouds/weather). Sorting and this depth proxy are separate material requirements.
    private static final RenderPipeline[] DEPTH_ONLY_PIPELINES = new RenderPipeline[Material.VALUES.length];

    private IndirectPipelines() {
    }

    public static RenderPipeline pipelineFor(Material material) {
        return PIPELINES[material.ordinal()];
    }

    public static RenderPipeline depthOnlyPipelineFor(Material material) {
        return DEPTH_ONLY_PIPELINES[material.ordinal()];
    }

    public static void register(RegisterRenderPipelinesEvent event) {
        for (Material material : Material.VALUES) {
            RenderPipeline pipeline = material == Material.CRUMBLING ? buildCrumblingPipeline() : buildPipeline(material);
            PIPELINES[material.ordinal()] = pipeline;
            event.registerPipeline(pipeline);

            if (material.requiresFabulousDepth) {
                RenderPipeline depthPipeline = buildDepthOnlyPipeline(material);
                DEPTH_ONLY_PIPELINES[material.ordinal()] = depthPipeline;
                event.registerPipeline(depthPipeline);
            }
        }
    }

    // Mirrors dev.golem.core.flywheel.Pipelines.buildCrumblingPipeline (INSTANCING side):
    // multiplicative blend (DST*SRC), depth-LE with depth-write OFF, polygon offset (-1, -10)
    // to overlay existing geometry without z-fighting. The shader pair declares the SSBO +
    // VisList SSBOs explicitly; Mojang doesn't manage them.
    private static RenderPipeline buildCrumblingPipeline() {
        return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "pipeline/golem_indirect_crumbling"))
                .withVertexShader(CRUMBLING_SHADER)
                .withFragmentShader(CRUMBLING_SHADER)
                .withSampler("Sampler0")
                .withVertexFormat(Pipelines.VERTEX_FORMAT, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -10.0F))
                .withColorTargetState(new ColorTargetState(new BlendFunction(SourceFactor.DST_COLOR, DestFactor.SRC_COLOR, SourceFactor.ONE, DestFactor.ZERO)))
                .withCull(true)
                .build();
    }

    private static RenderPipeline buildPipeline(Material material) {
        String materialName = material.name().toLowerCase();
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "pipeline/golem_indirect_" + materialName))
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withSampler("Sampler2")
                .withUniform("ShaderLights", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(Pipelines.VERTEX_FORMAT, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, material.depthWrite))
                .withCull(material.cull);

        if (material.disableLighting) {
            builder.withShaderDefine("NO_LIGHTING");
        }

        switch (material) {
            case CUTOUT:
            case CUTOUT_NO_CULL:
                builder.withShaderDefine("ALPHA_CUTOUT", 0.1F);
                break;
            case CUTOUT_CLIP_SLAB:
                builder.withShaderDefine("ALPHA_CUTOUT", 0.1F);
                builder.withShaderDefine("CLIP_SLAB");
                break;
            case CUTOUT_CLIP_HALFSPACE:
                builder.withShaderDefine("ALPHA_CUTOUT", 0.1F);
                builder.withShaderDefine("CLIP_HALFSPACE");
                break;
            case TRANSLUCENT:
            case TRANSLUCENT_NO_CULL:
            case TRANSLUCENT_NO_DEPTH_WRITE:
            case TRANSLUCENT_NO_DEPTH_WRITE_NO_CULL:
                builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
                break;
            case TRANSLUCENT_SORTED_NO_CULL:
                builder.withShaderDefine("ALPHA_CUTOUT", 0.1F);
                builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
                break;
            case SOURCE_ALPHA_ADDITIVE_NO_CULL:
                builder.withShaderDefine("NO_DYNAMIC_LIGHTS");
                builder.withShaderDefine("NO_FOG");
                builder.withColorTargetState(new ColorTargetState(new BlendFunction(
                        SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ZERO)));
                break;
            case ADDITIVE:
            case ADDITIVE_NO_CULL:
                builder.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE));
                break;
            default:
                break;
        }

        return builder.build();
    }

    // Depth-only sibling of buildPipeline(material). Uses a purpose-built shader pair
    // (golem_indirect_depth.vsh/.fsh) that emits only gl_Position + texCoord0 in the vertex
    // stage and only does an alpha discard in the fragment stage — saves the lighting,
    // overlay, lightmap, and fog work the color pipeline does, all of which is masked away
    // by ColorTargetState.WRITE_NONE anyway. ALPHA_CUTOUT discards fully-transparent quad
    // corners so we don't write phantom particle depth where the layer's color is alpha=0.
    //
    // Only declares Sampler0 (alpha texture); Sampler1/Sampler2 (overlay/lightmap) aren't
    // sampled by the minimal fsh, so they don't need to be declared on this pipeline. The
    // bind calls for them in IndirectDrawManager.drawStage live above the loop and are no-ops
    // for this pipeline at draw-validation time (extra bindings are tolerated; only missing
    // ones error).
    //
    // Only invoked for materials with requiresFabulousDepth=true. The depth pass is a Fabulous-
    // mode add-on to inform the transparency post-chain's per-pixel layer sort; in Fancy/Fast
    // and shader-pack pipelines IndirectDrawManager doesn't run it at all.
    private static RenderPipeline buildDepthOnlyPipeline(Material material) {
        String materialName = material.name().toLowerCase();
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "pipeline/golem_indirect_depth_" + materialName))
                .withVertexShader(DEPTH_ONLY_SHADER)
                .withFragmentShader(DEPTH_ONLY_SHADER)
                .withSampler("Sampler0")
                .withVertexFormat(Pipelines.VERTEX_FORMAT, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
                .withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_NONE))
                .withCull(material.cull)
                .withShaderDefine("ALPHA_CUTOUT", 0.05F)
                .build();
    }
}
//?}
