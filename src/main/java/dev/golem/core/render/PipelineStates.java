package dev.golem.core.render;

//? if >=1.21.11 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
//?}
//? if >=26.2 {
/*import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.shaders.UniformType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.BindGroupLayouts;
*///?}
//? if >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.ShaderType;
*///?}
//? if >=26.1 {
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import java.util.Optional;
//?} elif >=1.21.11 {
/*import com.mojang.blaze3d.platform.DepthTestFunction;
*///?}
//? if >=1.21.11 && <26.2 {
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
//?}

/**
 * Blend, colour-mask and depth state on a pipeline builder. 26.1 groups them into {@code ColorTargetState} and
 * {@code DepthStencilState}; 1.21.11 sets each on the builder. 1.21.11 has no always-pass test, so {@link Depth#ALWAYS}
 * turns the depth test off there, which also stops depth writes. 26.2 binds samplers and uniforms through bind group
 * layouts and draws with reversed depth, so {@link Depth} means "nearer" there too.
 */
//? if >=1.21.11 {
public final class PipelineStates {

    public enum Depth { LESS_OR_EQUAL, EQUAL, ALWAYS }

    /** Blend factors by name: 26.2 has one enum where older nodes split source and destination. */
    public enum Factor {
        ZERO, ONE, SRC_COLOR, ONE_MINUS_SRC_COLOR, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, DST_ALPHA, ONE_MINUS_DST_ALPHA,
        DST_COLOR, ONE_MINUS_DST_COLOR
    }

    //? if >=26.2 {
    /*/^* 26.1's snippet of the same name: model-view and projection, no globals or fog. ^/
    //? if >=26.3 {
    /^public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS).withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .buildSnippet();
    ^///?} else {
    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION).buildSnippet();
    //?}
    public static final RenderPipeline.Snippet FOG_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.FOG).buildSnippet();
    private static final Map<String, BindGroupLayout> SAMPLERS = new ConcurrentHashMap<>();
    *///?}

    private PipelineStates() {}

    /** The vertex format and how its vertices form primitives, set together on every node. */
    public static RenderPipeline.Builder vertices(RenderPipeline.Builder builder, VertexFormat format, VertexFormat.Mode mode) {
        //? if >=26.2 {
        /*return builder.withVertexBinding(0, format).withPrimitiveTopology(mode);
        *///?} else {
        return builder.withVertexFormat(format, mode);
        //?}
    }

    //? if >=26.2 {
    /*/^* A layout holding one sampler, shared by every pipeline naming it. ^/
    public static BindGroupLayout sampler(String name) {
        //? if >=26.3 {
        /^return SAMPLERS.computeIfAbsent(name, n -> BindGroupLayout.builder()
                .withUniform(n, UniformType.COMBINED_IMAGE_SAMPLER).build());
        ^///?} else {
        return SAMPLERS.computeIfAbsent(name, n -> BindGroupLayout.builder().withSampler(n).build());
        //?}
    }

    public static BindGroupLayout uniform(String name) {
        return BindGroupLayout.builder().withUniform(name, UniformType.UNIFORM_BUFFER).build();
    }
    *///?}

    public static BlendFunction blendFunction(Factor srcColor, Factor dstColor, Factor srcAlpha, Factor dstAlpha) {
        //? if >=26.2 {
        /*return new BlendFunction(BlendFactor.valueOf(srcColor.name()), BlendFactor.valueOf(dstColor.name()),
                BlendFactor.valueOf(srcAlpha.name()), BlendFactor.valueOf(dstAlpha.name()));
        *///?} else {
        return new BlendFunction(SourceFactor.valueOf(srcColor.name()), DestFactor.valueOf(dstColor.name()),
                SourceFactor.valueOf(srcAlpha.name()), DestFactor.valueOf(dstAlpha.name()));
        //?}
    }

    /** What a pipeline samples, by sampler name: the part of its layout a mirror of it must match. */
    public static Set<String> samplers(RenderPipeline pipeline) {
        //? if >=26.3 {
        /*Set<String> names = new HashSet<>();
        for (var uniform : BindGroupLayout.flattenUniforms(pipeline.getBindGroupLayouts())) {
            if (uniform.type() == UniformType.COMBINED_IMAGE_SAMPLER) names.add(uniform.name());
        }
        return names;
        *///?} elif >=26.2 {
        /*return new HashSet<>(BindGroupLayout.flattenSamplers(pipeline.getBindGroupLayouts()));
        *///?} else {
        return new HashSet<>(pipeline.getSamplers());
        //?}
    }

    public static Identifier fragmentShader(RenderPipeline pipeline) {
        //? if >=26.3 {
        /*return pipeline.getShaders().get(ShaderType.FRAGMENT);
        *///?} else {
        return pipeline.getFragmentShader();
        //?}
    }

    public static Identifier vertexShader(RenderPipeline pipeline) {
        //? if >=26.3 {
        /*return pipeline.getShaders().get(ShaderType.VERTEX);
        *///?} else {
        return pipeline.getVertexShader();
        //?}
    }

    //? if >=26.1 {
    /** The pipeline's colour targets, compared by value. */
    public static Object colorTargets(RenderPipeline pipeline) {
        //? if >=26.3 {
        /*return pipeline.getColorTargetStates();
        *///?} else {
        return pipeline.getColorTargetState();
        //?}
    }
    //?}

    /** Blends with {@code blend} and writes every channel. */
    public static RenderPipeline.Builder blend(RenderPipeline.Builder builder, BlendFunction blend) {
        return color(builder, blend, true, true);
    }

    /** {@code blend} null draws unblended. */
    public static RenderPipeline.Builder color(RenderPipeline.Builder builder, @Nullable BlendFunction blend,
                                               boolean writeColor, boolean writeAlpha) {
        //? if >=26.1 {
        int mask = (writeColor ? ColorTargetState.WRITE_COLOR : 0) | (writeAlpha ? ColorTargetState.WRITE_ALPHA : 0);
        //? if >=26.2 {
        /*return builder.withColorTargetState(new ColorTargetState(Optional.ofNullable(blend), GpuFormat.RGBA8_UNORM, mask));
        *///?} else {
        return builder.withColorTargetState(new ColorTargetState(Optional.ofNullable(blend), mask));
        //?}
        //?} else {
        /*if (blend == null) builder.withoutBlend();
        else builder.withBlend(blend);
        return builder.withColorWrite(writeColor, writeAlpha);
        *///?}
    }

    public static RenderPipeline.Builder depth(RenderPipeline.Builder builder, Depth test, boolean write) {
        //? if >=26.1 {
        return builder.withDepthStencilState(new DepthStencilState(op(test), write));
        //?} else {
        /*return builder.withDepthTestFunction(function(test)).withDepthWrite(write && test != Depth.ALWAYS);
        *///?}
    }

    public static RenderPipeline.Builder depth(RenderPipeline.Builder builder, Depth test, boolean write,
                                               float biasScale, float biasConstant) {
        //? if >=26.1 {
        //? if >=26.2 {
        /*return builder.withDepthStencilState(new DepthStencilState(op(test), write, -biasScale, -biasConstant));
        *///?} else {
        return builder.withDepthStencilState(new DepthStencilState(op(test), write, biasScale, biasConstant));
        //?}
        //?} else {
        /*return depth(builder, test, write).withDepthBias(biasScale, biasConstant);
        *///?}
    }

    /** No depth test and no depth write. */
    public static RenderPipeline.Builder noDepth(RenderPipeline.Builder builder) {
        //? if >=26.1 {
        return builder.withDepthStencilState(Optional.empty());
        //?} else {
        /*return builder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false);
        *///?}
    }

    /** Copies {@code source}'s depth test, write and bias. */
    public static RenderPipeline.Builder depthOf(RenderPipeline.Builder builder, RenderPipeline source) {
        //? if >=26.1 {
        return builder.withDepthStencilState(Optional.ofNullable(source.getDepthStencilState()));
        //?} else {
        /*return builder.withDepthTestFunction(source.getDepthTestFunction()).withDepthWrite(source.isWriteDepth())
                .withDepthBias(source.getDepthBiasScaleFactor(), source.getDepthBiasConstant());
        *///?}
    }

    //? if >=26.1 {
    private static CompareOp op(Depth test) {
        return switch (test) {
            //? if >=26.2 {
            /*case LESS_OR_EQUAL -> CompareOp.GREATER_THAN_OR_EQUAL;
            *///?} else {
            case LESS_OR_EQUAL -> CompareOp.LESS_THAN_OR_EQUAL;
            //?}
            case EQUAL -> CompareOp.EQUAL;
            case ALWAYS -> CompareOp.ALWAYS_PASS;
        };
    }
    //?} else {
    /*private static DepthTestFunction function(Depth test) {
        return switch (test) {
            case LESS_OR_EQUAL -> DepthTestFunction.LEQUAL_DEPTH_TEST;
            case EQUAL -> DepthTestFunction.EQUAL_DEPTH_TEST;
            case ALWAYS -> DepthTestFunction.NO_DEPTH_TEST;
        };
    }
    *///?}
}
//?} else {
/*public final class PipelineStates {
    private PipelineStates() {}
}
*///?}
