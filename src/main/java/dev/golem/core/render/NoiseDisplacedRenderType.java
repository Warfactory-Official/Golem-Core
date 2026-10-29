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
 * {@code UV0.y} carries the per-vertex time (1.12's {@code time} uniform, set per particle). Positions arrive
 * already posed (render space), so the object-space offset is mapped by {@link #localMatrix}: on 26.1 through the
 * render type's texture transform ({@code TextureMat}), before 26.1 through a {@code LocalMat} uniform set when the
 * type's state is applied. Either way the matrix is read when the batch is drawn, after every submission of the
 * frame, so each drawing object that needs its own matrix takes its own <em>slot</em> ({@code 0 .. SLOTS - 1}), a
 * separate render type. Slot 0 is the one-argument form (a first-person effect drawn once per frame);
 * third-person users hand out the others per frame.
 *
 * <p>Resources: {@code <namespace>:shaders/core/<name>.vsh/.fsh} on 26.1 (pipeline {@code <namespace>:pipeline/<name>});
 * below 26.1 the program {@code <namespace>:shaders/core/<name>.json} (its stages may be named differently), loaded through
 * {@link ShaderLoader} (the Fabric program-name trap is handled there). The JSON must <em>not</em> state a blend: on
 * 1.20.1 (1.21.1's {@code ShaderInstance} does not read one) a program blend becomes {@code BlendMode.lastApplied}, and the next vanilla core shader whose (opaque) mode
 * differs then disables blending after its own render type enabled it (vignette, HUD and translucent geometry drawn
 * opaque). The render type's {@code LIGHTNING_TRANSPARENCY} sets {@code SRC_ALPHA / ONE}; the program's default
 * (opaque) blend that {@code apply()} re-applies over it is undone after every {@code apply()}, as {@link Bloom} does.
 *
 * <p><b>Textured variant</b> ({@link #textured}): the same state with a second texture, {@code Sampler1}, for the
 * fragment stage, and in place of the local matrix a full parameter matrix ({@link #params}) the shader reads as it
 * likes, translation included: on 26.1 {@code TextureMat}, before 26.1 {@code LocalMat}. HBM 1.12's gluon beam is the
 * user: its vertex shaders displace each vertex in the beam's own space by noise and the time, billboard against the
 * camera, and the fragment stage samples the beam texture.
 */
public final class NoiseDisplacedRenderType {

    /** How many independent local matrices (and render types per shader, noise and target) exist. */
    public static final int SLOTS = 16;

    private static final Matrix4f[] LOCAL = new Matrix4f[SLOTS];
    private static final Matrix4f[] PARAMS = new Matrix4f[SLOTS];

    static {
        for (int i = 0; i < SLOTS; i++) {
            LOCAL[i] = new Matrix4f();
            PARAMS[i] = new Matrix4f();
        }
    }

    private NoiseDisplacedRenderType() {}

    /** The linear map from the effect's object space to the posed (render) space of the vertices submitted next. */
    public static void localMatrix(Matrix4f matrix) {
        localMatrix(0, matrix);
    }

    /** {@link #localMatrix(Matrix4f)} for the render types of {@code slot} (the four-argument {@code of}). */
    public static void localMatrix(int slot, Matrix4f matrix) {
        LOCAL[slot].set(matrix).setTranslation(0F, 0F, 0F);
    }

    /** The parameter matrix the {@link #textured} render types of {@code slot} hand their shader, kept whole. */
    public static void params(int slot, Matrix4f matrix) {
        PARAMS[slot].set(matrix);
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
    private static final TextureTransform[] LOCAL_TRANSFORMS = new TextureTransform[SLOTS];

    static {
        for (int i = 0; i < SLOTS; i++) {
            Matrix4f local = LOCAL[i];
            LOCAL_TRANSFORMS[i] = new TextureTransform(i == 0 ? "golem_noise_local" : "golem_noise_local_" + i,
                    () -> new Matrix4f(local));
        }
    }
    private static final TextureTransform[] PARAM_TRANSFORMS = new TextureTransform[SLOTS];

    static {
        for (int i = 0; i < SLOTS; i++) {
            Matrix4f params = PARAMS[i];
            PARAM_TRANSFORMS[i] = new TextureTransform("golem_noise_params_" + i, () -> new Matrix4f(params));
        }
    }
    private static final OutputTarget BLOOM_OUTPUT = new OutputTarget("golem_bloom_layer", Bloom.LAYER::drawTarget);

    /**
     * The render type for {@code shaderId} ({@code namespace:name}) sampling {@code noise} as HBM 1.12 sampled its noise
     * textures ({@link NoiseTextures}: repeat, nearest minification, linear magnification; the shader's
     * {@code texture2DLod(noise, uv, 0)} magnifies, so bilinear); {@code bloom} draws into the bloom layer instead of the
     * main target.
     */
    public static RenderType of(String shaderId, Identifier noise, boolean bloom) {
        return of(shaderId, noise, bloom, 0);
    }

    /** The three-argument {@code of} reading the local matrix of {@code slot}. */
    public static RenderType of(String shaderId, Identifier noise, boolean bloom, int slot) {
        String key = shaderId + "\n" + noise + "\n" + bloom + "\n" + slot;
        return TYPES.computeIfAbsent(key, k -> create(shaderId, noise, bloom, slot));
    }

    /**
     * The textured variant for {@code shaderId}: {@code Sampler0} is {@code noise} as {@link #of}, {@code Sampler1} is
     * {@code texture} sampled the same way (1.12 bound its beam textures with the same linear magnification), and
     * {@code TextureMat} is the parameter matrix of {@code slot}.
     */
    public static RenderType textured(String shaderId, Identifier noise, Identifier texture, int slot) {
        String key = shaderId + "\n" + noise + "\n" + texture + "\n" + slot + "\ntextured";
        return TYPES.computeIfAbsent(key, k -> createTextured(shaderId, noise, texture, slot));
    }

    private static RenderType createTextured(String shaderId, Identifier noise, Identifier texture, int slot) {
        Identifier base = dev.golem.core.Ids.parse(shaderId);
        RenderPipeline pipeline = WorldPipelines.add(RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(dev.golem.core.Ids.of(base.getNamespace(), "pipeline/" + base.getPath()))
                .withVertexShader(dev.golem.core.Ids.of(base.getNamespace(), "core/" + base.getPath()))
                .withFragmentShader(dev.golem.core.Ids.of(base.getNamespace(), "core/" + base.getPath()))
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
                .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
                .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                .withCull(false)
                .build());
        RenderSetup setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", noise, NoiseTextures::sampler)
                .withTexture("Sampler1", texture, NoiseTextures::sampler)
                .setTextureTransform(PARAM_TRANSFORMS[slot])
                .createRenderSetup();
        return RenderType.create("golem_noise_displaced_textured", setup);
    }

    private static RenderType create(String shaderId, Identifier noise, boolean bloom, int slot) {
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
                .withTexture("Sampler0", noise, NoiseTextures::sampler)
                .setTextureTransform(LOCAL_TRANSFORMS[slot]);
        if (bloom) setup.setOutputTarget(BLOOM_OUTPUT);
        else WorldPipelines.add(pipeline);
        return RenderType.create("golem_noise_displaced", setup.createRenderSetup());
    }
    //?} else {
    /*private static final Map<String, ShaderInstance> SHADERS = new HashMap<>();
    private static boolean reloadRegistered;

    public static RenderType of(String shaderId, ResourceLocation noise, boolean bloom) {
        return of(shaderId, noise, bloom, 0);
    }

    public static RenderType of(String shaderId, ResourceLocation noise, boolean bloom, int slot) {
        watchReloads();
        String key = shaderId + "\n" + noise + "\n" + bloom + "\n" + slot;
        return TYPES.computeIfAbsent(key, k -> Factory.create(shaderId, noise, bloom, slot));
    }

    private static void watchReloads() {
        if (reloadRegistered) return;
        reloadRegistered = true;
        dev.golem.core.client.ClientResourceReloads.register("golemcore", "noise_displaced", () -> {
            SHADERS.values().forEach(ShaderInstance::close);
            SHADERS.clear();
            TYPES.clear();
        });
    }

    public static RenderType textured(String shaderId, ResourceLocation noise, ResourceLocation texture, int slot) {
        watchReloads();
        String key = shaderId + "\n" + noise + "\n" + texture + "\n" + slot + "\ntextured";
        return TYPES.computeIfAbsent(key, k -> Factory.createTextured(shaderId, noise, texture, slot));
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
            private final int slot;

            private final ResourceLocation noiseId;

            private NoiseTexture(ResourceLocation noise, String shaderId, int slot) {
                super(noise, false, false);
                this.noiseId = noise;
                this.shaderId = shaderId;
                this.slot = slot;
            }

            @Override
            public void setupRenderState() {
                super.setupRenderState();
                NoiseTextures.apply(Minecraft.getInstance().getTextureManager().getTexture(noiseId));
                shader(shaderId).safeGetUniform("LocalMat").set(new Matrix4f(LOCAL[slot]));
            }
        }

        /^* Noise on unit 0, the fragment stage's texture on unit 1, and the whole parameter matrix as {@code LocalMat}. ^/
        private static final class TexturedState extends TextureStateShard {
            private final String shaderId;
            private final int slot;
            private final ResourceLocation noiseId;
            private final ResourceLocation textureId;

            private TexturedState(ResourceLocation noise, ResourceLocation texture, String shaderId, int slot) {
                super(noise, false, false);
                this.noiseId = noise;
                this.textureId = texture;
                this.shaderId = shaderId;
                this.slot = slot;
            }

            @Override
            public void setupRenderState() {
                super.setupRenderState();
                var textures = Minecraft.getInstance().getTextureManager();
                com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(1, textureId);
                NoiseTextures.apply(textures.getTexture(textureId));
                NoiseTextures.apply(textures.getTexture(noiseId));
                shader(shaderId).safeGetUniform("LocalMat").set(new Matrix4f(PARAMS[slot]));
            }
        }

        static RenderType createTextured(String shaderId, ResourceLocation noise, ResourceLocation texture, int slot) {
            CompositeState state = CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader(shaderId)))
                    .setTextureState(new TexturedState(noise, texture, shaderId, slot))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false);
            return RenderType.create("golem_noise_displaced_textured", DefaultVertexFormat.POSITION_TEX_COLOR,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 256, false, true, state);
        }

        static RenderType create(String shaderId, ResourceLocation noise, boolean bloom, int slot) {
            CompositeState.CompositeStateBuilder state = CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader(shaderId)))
                    .setTextureState(new NoiseTexture(noise, shaderId, slot))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE);
            if (bloom) state.setOutputState(BLOOM_OUTPUT);
            RenderType type = RenderType.create("golem_noise_displaced", DefaultVertexFormat.POSITION_TEX_COLOR,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 256, false, true, state.createCompositeState(false));
            // the bloom variant binds the bloom layer: an empty batch must not mark it used (ScreenEffects.skipEmpty)
            return bloom ? ScreenEffects.skipEmpty(type, true) : type;
        }
    }
    *///?}
}
