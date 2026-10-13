package dev.golem.core.flywheel;

//? if >=26.1 {

import dev.golem.core.GolemCore;
import dev.golem.core.flywheel.instance.AbstractInstancer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL31;

public final class Pipelines {

    // Sized at runtime from GL_MAX_UNIFORM_BLOCK_SIZE inside build(). Both vertex shaders read it
    // from the pipeline-injected #define INSTANCE_CAP and have no inline fallback, so build() must
    // precede the first bind. Every route to a pipeline goes through pipelineFor, which guarantees that.
    public static int INSTANCE_CAP;

    // Hard upper cap. The instanced engine chunks per-bucket draws at INSTANCE_CAP instances,
    // and the only Golem machine bucketed today (assembly machine) tops out at 12 instances per
    // tile, so very large caps just waste UBO storage without reducing draw count. 1024 leaves
    // plenty of room for future high-density meshes (cable bundles, etc.) and still fits in the
    // 64 KB UBO guarantee at 96 B/instance.
    private static final int MAX_INSTANCE_CAP = 1024;
    private static final int MIN_INSTANCE_CAP = 64;

    public static final VertexFormat VERTEX_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Normal", VertexFormatElement.NORMAL)
            .padding(1)
            .build();

    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "core/golem_flywheel");
    private static final Identifier CRUMBLING_SHADER = Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "core/golem_flywheel_crumbling");

    private static final RenderPipeline[] PIPELINES = new RenderPipeline[Material.VALUES.length];

    private static boolean built;

    private Pipelines() {
    }

    public static RenderPipeline pipelineFor(Material material) {
        build();
        return PIPELINES[material.ordinal()];
    }

    // Queries GL, so it may only run on the render thread. NeoForge has a registry event that
    // satisfies that and precompiles the result; Fabric has none, and a pipeline it was never
    // told about still compiles on first bind, so there the first draw builds them.
    public static synchronized void build() {
        if (built) return;
        built = true;
        INSTANCE_CAP = computeInstanceCap();
        for (Material material : Material.VALUES) {
            PIPELINES[material.ordinal()] = buildPipeline(material);
        }
    }

    //? if neoforge {
    public static void register(net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent event) {
        build();
        for (RenderPipeline pipeline : PIPELINES) {
            event.registerPipeline(pipeline);
        }
    }
    //?}

    private static int computeInstanceCap() {
        int maxBlockBytes = GL31.glGetInteger(GL31.GL_MAX_UNIFORM_BLOCK_SIZE);
        int maxFromGl = maxBlockBytes / AbstractInstancer.STD140_BYTES;
        // InstancedDrawManager chunks per-bucket draws at INSTANCE_CAP instances and binds
        // each chunk via GpuBufferSlice at byteOffset = chunkStart * STD140_BYTES. Mojang's
        // RenderPass.setUniform asserts byteOffset is aligned to GL_UNIFORM_BUFFER_OFFSET_-
        // ALIGNMENT (256 on most NVIDIA drivers, sometimes 64 / 16 elsewhere). For every
        // chunkStart = k * cap to land on an aligned offset, cap * STD140_BYTES must itself
        // be a multiple of the alignment, i.e. cap must be a multiple of
        // alignment / gcd(alignment, STD140_BYTES). With STD140_BYTES = 112 and alignment =
        // 256 the step is 16, so 585 (the raw value from a 64 KB UBO) gets snapped to 576.
        int alignment = GL31.glGetInteger(GL31.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT);
        int step = alignment / gcd(alignment, AbstractInstancer.STD140_BYTES);
        int snapped = maxFromGl - (maxFromGl % step);
        return Math.max(MIN_INSTANCE_CAP, Math.min(MAX_INSTANCE_CAP, snapped));
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    private static RenderPipeline buildPipeline(Material material) {
        if (material == Material.CRUMBLING) {
            return buildCrumblingPipeline();
        }
        String materialName = material.name().toLowerCase();
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "pipeline/golem_flywheel_" + materialName))
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withSampler("Sampler2")
                .withUniform("Instances", UniformType.UNIFORM_BUFFER)
                .withUniform("ShaderLights", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(VERTEX_FORMAT, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, material.depthWrite))
                .withCull(material.cull)
                .withShaderDefine("INSTANCE_CAP", INSTANCE_CAP);

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
                // Per-instance slab: plane equation in UVTransform, slide in InstanceColor.rgb.
                // See Material.CUTOUT_CLIP_SLAB.
                builder.withShaderDefine("CLIP_SLAB");
                break;
            case CUTOUT_CLIP_HALFSPACE:
                builder.withShaderDefine("ALPHA_CUTOUT", 0.1F);
                // Per-instance half-space: same UVTransform/InstanceColor use as CLIP_SLAB, with a
                // single-sided discard test. See Material.CUTOUT_CLIP_HALFSPACE.
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
                // Tested on the texel alone, as vanilla's entityTranslucent (the fallback draw of the same rows)
                // tests it: the composed texel x instance alpha cut 1.7's Torex cloudlets off before their fade ended.
                builder.withShaderDefine("ALPHA_CUTOUT_TEXTURE");
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

    // Mirrors vanilla RenderPipelines.CRUMBLING (RenderPipelines.java:403): multiplicative blend
    // (DST*SRC), depth-test LE with depth-write OFF and polygon offset (-1, -10) to overlay the
    // existing geometry without z-fighting. Our crumbling shader pair strips lighting and overlay
    // sampling so destroy_stage textures pass straight through to the blender.
    private static RenderPipeline buildCrumblingPipeline() {
        return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "pipeline/golem_flywheel_crumbling"))
                .withVertexShader(CRUMBLING_SHADER)
                .withFragmentShader(CRUMBLING_SHADER)
                .withSampler("Sampler0")
                .withUniform("Instances", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(VERTEX_FORMAT, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -10.0F))
                .withColorTargetState(new ColorTargetState(new BlendFunction(SourceFactor.DST_COLOR, DestFactor.SRC_COLOR, SourceFactor.ONE, DestFactor.ZERO)))
                .withCull(true)
                .withShaderDefine("INSTANCE_CAP", INSTANCE_CAP)
                .build();
    }
}
//?}
