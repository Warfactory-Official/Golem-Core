package dev.golem.core.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
//?} else {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.pipeline.TextureTarget;
import java.io.IOException;
*///?}

/**
 * Scene-sampling render type for the jetpack heat shimmer.
 *
 * <p>This is not {@link CompatRenderTypes#entityTranslucent}: that type can only blend a
 * colored quad. The old particle samples the already-rendered scene through a fresnel mask and animated
 * noise, so replacing it with a glow billboard changes the effect completely. The scene is copied to a
 * detached target at the first actual distortion draw, never during client ticking or state extraction.
 */
public final class HeatDistortionRenderType {

    private static String pipelineId;
    private static String shaderId;
    private static boolean captureReported;

    private HeatDistortionRenderType() {}

    /**
     * Supplies the resource names for this scene-sampling effect.
     *
     * <p>The consumer owns the namespace and resource names. Core only maps the supplied base shader id
     * to the platform shader locations: the legacy {@code ShaderInstance} uses the base id and the 26.1
     * pipeline uses its {@code shaders/core/} sibling.
     */
    public static void configure(String pipelineId, String shaderId) {
        if (pipelineId == null || shaderId == null || pipelineId.isBlank() || shaderId.isBlank()) {
            throw new IllegalArgumentException("Heat distortion requires pipeline and shader ids");
        }
        HeatDistortionRenderType.pipelineId = pipelineId;
        HeatDistortionRenderType.shaderId = shaderId;
        //? if >=26.1 {
        RenderTypes.clear();
        //?} else {
        /*clear();
        *///?}
        registerReloads();
        resetScene();
    }

    private static String require(String id, String label) {
        if (id == null) throw new IllegalStateException("Heat distortion " + label + " was not configured");
        return id;
    }

    private static String modernShaderId() {
        String base = require(shaderId, "shader");
        int separator = base.indexOf(':');
        if (separator < 0) throw new IllegalArgumentException("Heat distortion shader id is not namespaced: " + base);
        return base.substring(0, separator + 1) + "core/" + base.substring(separator + 1);
    }

    private static String legacyShaderId() {
        String base = require(shaderId, "shader");
        int separator = base.indexOf(':');
        return separator < 0 ? base : base.substring(separator + 1);
    }

    private static float wallClockSeconds() {
        return (System.currentTimeMillis() % 10_000_000L) / 1000F;
    }

    // The modern pipeline has no scalar custom-uniform binding. UV1 is two signed shorts, so use both
    // components as the low/high halves of a normalized 32-bit phase instead of abusing vertex color.
    private static final double MODERN_TIME_RANGE = 16_384.0D;

    //? if >=26.1 {
    private static final com.mojang.blaze3d.vertex.VertexFormat HEAT_FORMAT =
            com.mojang.blaze3d.vertex.VertexFormat.builder()
                    .add("Position", com.mojang.blaze3d.vertex.VertexFormatElement.POSITION)
                    .add("UV0", com.mojang.blaze3d.vertex.VertexFormatElement.UV0)
                    .add("Color", com.mojang.blaze3d.vertex.VertexFormatElement.COLOR)
                    .add("UV1", com.mojang.blaze3d.vertex.VertexFormatElement.UV1)
                    .build();

    private static int[] encodeModernTime(float timeOffset) {
        double phase = (double) timeOffset * 20.0D + wallClockSeconds();
        long encoded = Math.round(Math.max(0.0D, Math.min(1.0D, phase / MODERN_TIME_RANGE))
                * 4_294_967_295.0D);
        return new int[] {(int) encoded, (int) (encoded >>> 16)};
    }
    //?}

    /** Emits the position/UV/amount vertex used by the shared custom shader. */
    public static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
            float u, float v, float amount, float timeOffset) {
        //? if >=26.1 {
        int[] encodedTime = encodeModernTime(timeOffset);
        buffer.addVertex(pose, x, y, z).setUv(u, v)
                .setColor(amount, 0F, 0F, 1F)
                .setUv1(encodedTime[0], encodedTime[1]);
        //?} else if >=1.21 {
        /*buffer.addVertex(pose, x, y, z).setUv(u, v)
                .setColor(amount, timeOffset, 0F, 1F);
        *///?} else {
        /*buffer.vertex(pose.pose(), x, y, z).uv(u, v).color(amount, timeOffset, 0F, 1F).endVertex();
        *///?}
    }

    private static void resetScene() {
        captureReported = false;
        //? if >=26.1 {
        SceneTexture.reset();
        //?} else {
        /*SceneCopy.reset();
        if (shader != null) {
            shader.close();
            shader = null;
        }
        *///?}
    }

    //? if >=26.1 {
    /** The render type is cached by the two mask/noise resources, like vanilla texture render types. */
    public static RenderType of(Identifier mask, Identifier noise) {
        require(shaderId, "shader");
        return RenderTypes.of(mask, noise);
    }

    static boolean isSceneSampling(RenderType renderType) {
        return RenderTypes.contains(renderType) || SceneWarp.isSceneSampling(renderType);
    }

    static void prepareSceneForPass() {
        SceneTexture.capture();
        reportCapture();
    }

    /** The detached scene copy, shared with {@link SceneWarp} so a pass is captured once. */
    static Identifier sceneTexture() {
        installSceneTexture();
        return SceneTexture.ID;
    }

    private static final class RenderTypes {
        private static final java.util.Map<String, RenderType> TYPES = new java.util.HashMap<>();

        private static void clear() {
            TYPES.clear();
        }

        private static boolean contains(RenderType renderType) {
            return TYPES.containsValue(renderType);
        }

        private static RenderType of(Identifier mask, Identifier noise) {
            String key = mask.toString() + "\n" + noise;
            return TYPES.computeIfAbsent(key, ignored -> create(mask, noise));
        }

        private static RenderType create(Identifier mask, Identifier noise) {
            RenderPipeline pipeline = RenderPipeline.builder(
                            RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                    .withLocation(dev.golem.core.Ids.parse(require(pipelineId, "pipeline")))
                    .withVertexShader(dev.golem.core.Ids.parse(modernShaderId()))
                    .withFragmentShader(dev.golem.core.Ids.parse(modernShaderId()))
                    .withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withSampler("Sampler2")
                    .withVertexFormat(HEAT_FORMAT,
                            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
                    .withDepthStencilState(new DepthStencilState(
                            com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false))
                    .withCull(false)
                    .withColorTargetState(new ColorTargetState(
                            com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
                    .build();

            installSceneTexture();
            return RenderType.create("golem_heat_distortion", RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", SceneTexture.ID,
                            () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                    .withTexture("Sampler1", mask,
                            () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                    .withTexture("Sampler2", noise, NoiseTextures::sampler)
                    .createRenderSetup());
        }
    }

    private static boolean reloadRegistered;

    private static void reportCapture() {
        if (!captureReported) {
            captureReported = true;
            dev.golem.core.GolemCore.LOG.debug("Heat distortion scene capture active");
        }
    }

    private static void registerReloads() {
        if (reloadRegistered) return;
        reloadRegistered = true;
        dev.golem.core.client.ClientResourceReloads.register("golemcore", "heat_distortion",
                () -> {
                    SceneTexture.reset();
                    RenderTypes.clear();
                });
    }

    private static void installSceneTexture() {
        SceneTexture.ensureRegistered();
        registerReloads();
    }

    /** An identifier-backed texture whose view is a detached copy of the current main target. */
    private static final class SceneTexture extends AbstractTexture {
        private static final Identifier ID = dev.golem.core.Ids.of("golemcore", "main_render_target");
        private static final SceneTexture INSTANCE = new SceneTexture();
        private static RenderTarget copy;

        private static void ensureRegistered() {
            Minecraft.getInstance().getTextureManager().register(ID, INSTANCE);
        }

        private static void reset() {
            if (copy != null) copy.destroyBuffers();
            copy = null;
        }

        private static void ensureCopy(RenderTarget main) {
            if (main == null || main.width <= 0 || main.height <= 0) return;
            if (copy == null || copy.width != main.width || copy.height != main.height) {
                if (copy != null) copy.destroyBuffers();
                copy = new TextureTarget("golem_heat_scene", main.width, main.height, false);
            }
        }

        private static void capture() {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            ensureCopy(main);
            // A raw copy, not blitAndBlendToTexture: that blends by the main target's alpha, and the sky below
            // the horizon is alpha 0 on 26.1, so every sampled sky pixel came out black.
            if (copy != null) RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    main.getColorTexture(), copy.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
        }

        @Override
        public com.mojang.blaze3d.textures.GpuTextureView getTextureView() {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            ensureCopy(main);
            return copy == null ? null : copy.getColorTextureView();
        }

        @Override
        public GpuSampler getSampler() {
            return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }
    //?} else {
    /*private static final java.util.Map<String, RenderType> TYPES = new java.util.HashMap<>();
    private static ShaderInstance shader;
    private static boolean reloadRegistered;

    private static void clear() {
        TYPES.clear();
    }

    static boolean isSceneSampling(RenderType renderType) {
        return TYPES.containsValue(renderType) || SceneWarp.isSceneSampling(renderType);
    }

    static void prepareSceneForPass() {
        SceneCopy.capture();
        reportCapture();
    }

    /^* The detached scene copy's GL texture, shared with {@link SceneWarp} so a pass is captured once. ^/
    static int sceneTextureId() {
        ensureReloadListener();
        return SceneCopy.textureId();
    }

    private static void ensureReloadListener() {
        if (reloadRegistered) return;
        reloadRegistered = true;
        dev.golem.core.client.ClientResourceReloads.register("golemcore", "heat_distortion", () -> {
            if (shader != null) shader.close();
            shader = null;
            SceneCopy.reset();
            TYPES.clear();
        });
    }

    private static void registerReloads() {
        ensureReloadListener();
    }

    private static void reportCapture() {
        if (!captureReported) {
            captureReported = true;
            dev.golem.core.GolemCore.LOG.debug("Heat distortion scene capture active");
        }
    }

    public static RenderType of(ResourceLocation mask, ResourceLocation noise) {
        require(shaderId, "shader");
        ensureReloadListener();
        String key = mask.toString() + "\n" + noise;
        return TYPES.computeIfAbsent(key, ignored -> create(mask, noise));
    }

    private static RenderType create(ResourceLocation mask, ResourceLocation noise) {
        return Factory.create(mask, noise);
    }

    private static ShaderInstance shader() {
        ensureReloadListener();
        if (shader == null) {
            try {
                shader = ShaderLoader.load(Minecraft.getInstance().getResourceManager(), require(shaderId, "shader"),
                        DefaultVertexFormat.POSITION_TEX_COLOR);
            } catch (IOException e) {
                throw new IllegalStateException("Could not load configured heat distortion shader", e);
            }
        }
        return shader;
    }

    private static final class SceneCopy {
        private static TextureTarget copy;

        private static void reset() {
            if (copy != null) copy.destroyBuffers();
            copy = null;
        }

        private static void ensureCopy(com.mojang.blaze3d.pipeline.RenderTarget main) {
            if (main == null || main.width <= 0 || main.height <= 0) return;
            if (copy == null || copy.width != main.width || copy.height != main.height) {
                reset();
                copy = new TextureTarget(main.width, main.height, false, false);
            }
        }

        private static void capture() {
            com.mojang.blaze3d.pipeline.RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            ensureCopy(main);
            if (copy == null) return;
            // Creating/resizing the detached target unbinds the framebuffer; bindRead only binds a texture.
            main.bindWrite(false);
            main.bindRead();
            RenderSystem.bindTexture(copy.getColorTextureId());
            GlStateManager._glCopyTexSubImage2D(3553, 0, 0, 0, 0, 0, main.width, main.height);
        }

        private static int textureId() {
            com.mojang.blaze3d.pipeline.RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            ensureCopy(main);
            return copy == null ? 0 : copy.getColorTextureId();
        }
    }

    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        private static final class SceneTextures extends TextureStateShard {
            private final ResourceLocation mask;
            private final ResourceLocation noise;

            private SceneTextures(ResourceLocation mask, ResourceLocation noise) {
                super(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS, false, false);
                this.mask = mask;
                this.noise = noise;
            }

            @Override
            public void setupRenderState() {
                super.setupRenderState();
                Minecraft minecraft = Minecraft.getInstance();
                RenderSystem.setShaderTexture(0, SceneCopy.textureId());
                RenderSystem.setShaderTexture(1, minecraft.getTextureManager().getTexture(mask).getId());
                // the noise as 1.12 sampled it: repeat, nearest minification, linear magnification
                NoiseTextures.apply(minecraft.getTextureManager().getTexture(noise));
                RenderSystem.setShaderTexture(2, minecraft.getTextureManager().getTexture(noise).getId());
                shader().safeGetUniform("windowSize").set((float) minecraft.getWindow().getWidth(),
                        (float) minecraft.getWindow().getHeight());
                shader().safeGetUniform("time").set(wallClockSeconds());
            }

            @Override
            public void clearRenderState() {
                RenderSystem.setShaderTexture(0, 0);
                RenderSystem.setShaderTexture(1, 0);
                RenderSystem.setShaderTexture(2, 0);
                super.clearRenderState();
            }
        }

        private static RenderType create(ResourceLocation mask, ResourceLocation noise) {
            return RenderType.create("golem_heat_distortion", DefaultVertexFormat.POSITION_TEX_COLOR,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 256, false, true,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(HeatDistortionRenderType::shader))
                            .setTextureState(new SceneTextures(mask, noise))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }
    }
    *///?}
}
