package dev.golem.core.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
*///?}

import java.util.HashMap;
import java.util.Map;

/**
 * A mod's own vertex shader that moves each vertex by a noise-texture lookup, drawn {@code SRC_ALPHA / ONE} (alpha
 * weighted additive), depth tested without writing, unculled, coloured by the vertex - into the main target or into
 * the {@link Bloom} layer. HBM 1.12's {@code crucible_lightning} is the user: it reads {@code noise} at the vertex's
 * {@code u} plus a per-particle {@code time} and offsets the position by the result <em>in the drawing object's own
 * space</em>.
 *
 * <p>The vertex format is {@code POSITION_TEX_COLOR}: {@code UV0.x} is the texture coordinate the shader samples with,
 * {@code UV0.y} carries the per-vertex time (the old program's {@code time} uniform, set per particle). Positions arrive
 * already posed (render space), so the object-space offset is mapped by {@link #localMatrix}: on 26.1 through the render
 * type's texture transform ({@code TextureMat}, read by the draw after submission in the same frame), before 26.1
 * through a {@code LocalMat} uniform set when the type's state is applied. One local matrix is current at a time, which
 * fits a first-person effect drawn once per frame.
 *
 * <p>Resources: {@code <namespace>:shaders/core/<name>.vsh/.fsh} on 26.1 (pipeline {@code <namespace>:pipeline/<name>});
 * below 26.1 the program {@code <namespace>:shaders/core/<name>.json} (its stages may be named differently), loaded through
 * {@link ShaderLoader} (the Fabric program-name trap is handled there). The JSON must <em>not</em> state a blend: on
 * 1.20.1 (1.21.1's {@code ShaderInstance} no longer reads one) a program blend becomes {@code BlendMode.lastApplied}, and the next vanilla core shader whose (opaque) mode
 * differs then disables blending after its own render type enabled it (vignette, HUD and translucent geometry drawn
 * opaque). The render type's {@code LIGHTNING_TRANSPARENCY} sets {@code SRC_ALPHA / ONE}; the program's default
 * (opaque) blend that {@code apply()} re-applies over it is undone after every {@code apply()}, as {@link Bloom} does.
 */
public final class NoiseDisplacedRenderType {

    private static final Matrix4f LOCAL = new Matrix4f();

    private NoiseDisplacedRenderType() {}

    /** The linear map from the effect's object space to the posed (render) space of the vertices submitted next. */
    public static void localMatrix(Matrix4f matrix) {
        LOCAL.set(matrix).setTranslation(0F, 0F, 0F);
    }

    /** One vertex: posed position, sample coordinate {@code u}, per-vertex {@code time}, ARGB colour. */
    public static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float time,
                              int argb) {
        //? if >=1.21 {
        buffer.addVertex(pose, x, y, z).setUv(u, time).setColor(argb);
        //?} else {
        /*buffer.vertex(pose.pose(), x, y, z).uv(u, time).color(argb).endVertex();
        *///?}
    }

    private static final Map<String, RenderType> TYPES = new HashMap<>();

    //? if >=26.1 {
    private static final TextureTransform LOCAL_TRANSFORM = new TextureTransform("golem_noise_local", () -> new Matrix4f(LOCAL));
    private static final OutputTarget BLOOM_OUTPUT = new OutputTarget("golem_bloom_layer", Bloom.LAYER::drawTarget);

    /**
     * The render type for {@code shaderId} ({@code namespace:name}) sampling {@code noise} with nearest filtering and
     * repeat wrapping; {@code bloom} draws into the bloom layer instead of the main target.
     */
    public static RenderType of(String shaderId, Identifier noise, boolean bloom) {
        String key = shaderId + "\n" + noise + "\n" + bloom;
        return TYPES.computeIfAbsent(key, k -> create(shaderId, noise, bloom));
    }

    private static RenderType create(String shaderId, Identifier noise, boolean bloom) {
        Identifier base = dev.golem.core.Ids.parse(shaderId);
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(dev.golem.core.Ids.of(base.getNamespace(), "pipeline/" + base.getPath() + (bloom ? "_bloom" : "")))
                .withVertexShader(dev.golem.core.Ids.of(base.getNamespace(), "core/" + base.getPath()))
                .withFragmentShader(dev.golem.core.Ids.of(base.getNamespace(), "core/" + base.getPath()))
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
                .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
                .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                .withCull(false)
                .build();
        RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", noise, () -> RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST))
                .setTextureTransform(LOCAL_TRANSFORM);
        if (bloom) setup.setOutputTarget(BLOOM_OUTPUT);
        return RenderType.create("golem_noise_displaced", setup.createRenderSetup());
    }
    //?} else {
    /*private static final Map<String, ShaderInstance> SHADERS = new HashMap<>();
    private static boolean reloadRegistered;

    public static RenderType of(String shaderId, ResourceLocation noise, boolean bloom) {
        if (!reloadRegistered) {
            reloadRegistered = true;
            dev.golem.core.client.ClientResourceReloads.register("golemcore", "noise_displaced", () -> {
                SHADERS.values().forEach(ShaderInstance::close);
                SHADERS.clear();
                TYPES.clear();
            });
        }
        String key = shaderId + "\n" + noise + "\n" + bloom;
        return TYPES.computeIfAbsent(key, k -> Factory.create(shaderId, noise, bloom));
    }

    private static ShaderInstance shader(String shaderId) {
        return SHADERS.computeIfAbsent(shaderId, id -> {
            try {
                return ShaderLoader.load(Minecraft.getInstance().getResourceManager(), id, DefaultVertexFormat.POSITION_TEX_COLOR,
                        () -> {
                            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                            com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                                    com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
                        });
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not load shader " + id, e);
            }
        });
    }

    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        private static final OutputStateShard BLOOM_OUTPUT = new OutputStateShard("golem_bloom_layer",
                Bloom.LAYER::bindForDraw, () -> Minecraft.getInstance().getMainRenderTarget().bindWrite(false));

        private static final class NoiseTexture extends TextureStateShard {
            private final String shaderId;

            private NoiseTexture(ResourceLocation noise, String shaderId) {
                super(noise, false, false);
                this.shaderId = shaderId;
            }

            @Override
            public void setupRenderState() {
                super.setupRenderState();
                shader(shaderId).safeGetUniform("LocalMat").set(new Matrix4f(LOCAL));
            }
        }

        static RenderType create(String shaderId, ResourceLocation noise, boolean bloom) {
            CompositeState.CompositeStateBuilder state = CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader(shaderId)))
                    .setTextureState(new NoiseTexture(noise, shaderId))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE);
            if (bloom) state.setOutputState(BLOOM_OUTPUT);
            return RenderType.create("golem_noise_displaced", DefaultVertexFormat.POSITION_TEX_COLOR,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 256, false, true, state.createCompositeState(false));
        }
    }
    *///?}
}
