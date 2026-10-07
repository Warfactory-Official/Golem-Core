package dev.golem.core.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
//?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
*///?}

/**
 * A render type whose consumer-supplied shader reads the already-rendered scene and draws over its own geometry
 * from it (gravitational lensing, refraction, ray-traced impostors).
 *
 * <p>Sampler0 is the same detached scene copy as {@link HeatDistortionRenderType}, captured once per pass at the
 * first scene-sampling draw, so it holds everything drawn before. Sampler1 is a texture chosen per render type.
 * The geometry is depth tested without writing depth and is blended translucently (the shader owns the alpha).
 *
 * <p>Seen through water or glass: the geometry is depth tested against the world <i>under</i> its translucent
 * layer (see {@code Translucency}), and the shader gets that layer to put back over itself. Sampler2 is the scene
 * as it was under the translucent layer, Sampler3's alpha is how much the layer covers each pixel, and Sampler4
 * is the world depth including the layer (sample all three with {@code texelFetch} at {@code gl_FragCoord}, or at
 * a projected point). Where Sampler4 is nearer than the fragment, the layer lies in front of it, and the shader's
 * result {@code H} becomes {@code scene + (H - under) * (1 - cover.a)}. Without a capture this frame (a shader pack
 * in use) Sampler2 and Sampler3 are the scene copy and the depth test is against the full world depth, so that
 * case never arises.
 *
 * <p>There are no custom uniforms (the 26.1 pipeline has no scalar custom-uniform binding), so parameters
 * travel in the vertex. The vertex is the entity format on every node: position, an 8-bit RGBA colour, UV0
 * (two floats), UV1 and UV2 (two 16-bit integers each) and a signed 8-bit normal, all of whose meanings belong
 * to the shader. The shader also gets the projection and model-view matrices, and the framebuffer size as
 * {@code ScreenSize} (26.1 globals) or the {@code windowSize} uniform (legacy).
 */
public final class SceneWarp {

    private static String pipelineId;
    private static String shaderId;
    private static boolean reloadRegistered;

    private SceneWarp() {}

    /**
     * Supplies the resource names: the 26.1 pipeline id, and the base shader id whose {@code core/} sibling is
     * the 26.1 program and whose JSON is the legacy {@code ShaderInstance}.
     */
    public static void configure(String pipelineId, String shaderId) {
        if (pipelineId == null || shaderId == null || pipelineId.isBlank() || shaderId.isBlank()) {
            throw new IllegalArgumentException("Scene warp requires pipeline and shader ids");
        }
        SceneWarp.pipelineId = pipelineId;
        SceneWarp.shaderId = shaderId;
        reset();
        if (!reloadRegistered) {
            reloadRegistered = true;
            dev.golem.core.client.ClientResourceReloads.register("golemcore", "scene_warp", SceneWarp::reset);
        }
    }

    public static boolean isConfigured() {
        return shaderId != null;
    }

    private static String require(String id, String label) {
        if (id == null) throw new IllegalStateException("Scene warp " + label + " was not configured");
        return id;
    }

    /**
     * One vertex. The normal is stored as given, not transformed by the pose; UV1/UV2 components are
     * 0..65535 and arrive in the shader as signed shorts.
     */
    public static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int argb,
            float u, float v, int uv1U, int uv1V, int uv2U, int uv2V, float normalX, float normalY, float normalZ) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        //? if >=1.21 {
        buffer.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v).setUv1(uv1U, uv1V).setUv2(uv2U, uv2V)
                .setNormal(normalX, normalY, normalZ);
        //?} else {
        /*buffer.vertex(pose.pose(), x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(uv1U, uv1V).uv2(uv2U, uv2V)
                .normal(normalX, normalY, normalZ).endVertex();
        *///?}
    }

    //? if >=26.1 {
    private static final java.util.Map<Identifier, RenderType> TYPES = new java.util.HashMap<>();

    /**
     * The entity format's elements in a format of its own. Iris extends a buffer whose format {@code ==}
     * {@code DefaultVertexFormat.ENTITY} and overwrites each quad's normals with its face normal, which would
     * destroy the parameter carried in Normal; it does not touch any other format instance.
     */
    private static final com.mojang.blaze3d.vertex.VertexFormat FORMAT = com.mojang.blaze3d.vertex.VertexFormat.builder()
            .add("Position", com.mojang.blaze3d.vertex.VertexFormatElement.POSITION)
            .add("Color", com.mojang.blaze3d.vertex.VertexFormatElement.COLOR)
            .add("UV0", com.mojang.blaze3d.vertex.VertexFormatElement.UV0)
            .add("UV1", com.mojang.blaze3d.vertex.VertexFormatElement.UV1)
            .add("UV2", com.mojang.blaze3d.vertex.VertexFormatElement.UV2)
            .add("Normal", com.mojang.blaze3d.vertex.VertexFormatElement.NORMAL)
            .padding(1)
            .build();

    private static void reset() {
        TYPES.clear();
    }

    static boolean isSceneSampling(RenderType renderType) {
        return renderType != null && TYPES.containsValue(renderType);
    }

    private static String modernShaderId() {
        String base = require(shaderId, "shader");
        int separator = base.indexOf(':');
        if (separator < 0) throw new IllegalArgumentException("Scene warp shader id is not namespaced: " + base);
        return base.substring(0, separator + 1) + "core/" + base.substring(separator + 1);
    }

    /** The render type with {@code texture} as Sampler1 (nearest, clamped). */
    public static RenderType renderType(Identifier texture) {
        return TYPES.computeIfAbsent(texture, SceneWarp::create);
    }

    private static RenderType create(Identifier texture) {
        RenderPipeline pipeline = RenderPipeline.builder(
                        RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                .withLocation(dev.golem.core.Ids.parse(require(pipelineId, "pipeline")))
                .withVertexShader(dev.golem.core.Ids.parse(modernShaderId()))
                .withFragmentShader(dev.golem.core.Ids.parse(modernShaderId()))
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withSampler("Sampler2")
                .withSampler("Sampler3")
                .withSampler("Sampler4")
                .withVertexFormat(FORMAT, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
                .withDepthStencilState(new DepthStencilState(
                        com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false))
                .withCull(false)
                .withColorTargetState(new ColorTargetState(
                        com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
                .build();
        return RenderType.create("golem_scene_warp", RenderSetup.builder(pipeline)
                .withTexture("Sampler0", HeatDistortionRenderType.sceneTexture(),
                        () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                .withTexture("Sampler1", texture,
                        () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
                .withTexture("Sampler2", Translucency.underTexture(),
                        () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                .withTexture("Sampler3", Translucency.coverTexture(),
                        () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
                .withTexture("Sampler4", Translucency.depthTexture(),
                        () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
                .createRenderSetup());
    }
    //?} else {
    /*private static final java.util.Map<ResourceLocation, RenderType> TYPES = new java.util.HashMap<>();
    private static ShaderInstance shader;

    private static void reset() {
        if (shader != null) shader.close();
        shader = null;
        TYPES.clear();
    }

    static boolean isSceneSampling(RenderType renderType) {
        return renderType != null && TYPES.containsValue(renderType);
    }

    /^* The render type with {@code texture} as Sampler1. ^/
    public static RenderType renderType(ResourceLocation texture) {
        return TYPES.computeIfAbsent(texture, Factory::create);
    }

    private static ShaderInstance shader() {
        if (shader == null) {
            try {
                shader = ShaderLoader.load(Minecraft.getInstance().getResourceManager(), require(shaderId, "shader"),
                        DefaultVertexFormat.NEW_ENTITY);
            } catch (IOException e) {
                throw new IllegalStateException("Could not load configured scene warp shader", e);
            }
        }
        return shader;
    }

    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        private static final class SceneTextureShard extends TextureStateShard {
            private final ResourceLocation texture;

            private SceneTextureShard(ResourceLocation texture) {
                super(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS, false, false);
                this.texture = texture;
            }

            @Override
            public void setupRenderState() {
                super.setupRenderState();
                Minecraft minecraft = Minecraft.getInstance();
                RenderSystem.setShaderTexture(0, HeatDistortionRenderType.sceneTextureId());
                RenderSystem.setShaderTexture(1, minecraft.getTextureManager().getTexture(texture).getId());
                RenderSystem.setShaderTexture(2, Translucency.underTextureId());
                RenderSystem.setShaderTexture(3, Translucency.coverTextureId());
                RenderSystem.setShaderTexture(4, Translucency.depthTextureId());
                shader().safeGetUniform("windowSize").set((float) minecraft.getWindow().getWidth(),
                        (float) minecraft.getWindow().getHeight());
            }

            @Override
            public void clearRenderState() {
                for (int i = 0; i <= 4; i++) RenderSystem.setShaderTexture(i, 0);
                super.clearRenderState();
            }
        }

        private static RenderType create(ResourceLocation texture) {
            require(shaderId, "shader");
            return RenderType.create("golem_scene_warp", DefaultVertexFormat.NEW_ENTITY,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 256, false, false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(SceneWarp::shader))
                            .setTextureState(new SceneTextureShard(texture))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }
    }
    *///?}
}
