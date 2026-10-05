package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;
//? if >=26.1 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
//?} else {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * One sky draw handed to a {@link Skyboxes.Skybox}: the level, the frame's sun position, and immediate-mode quads in
 * the sky's own space, drawn the way a 1.7 {@code IRenderHandler} drew them - no fog, no depth test or write, culling as
 * vanilla's sky, and the blend function picked per call.
 *
 * <p>{@link #pose()} starts as vanilla's sky pose (camera rotation only, origin at the eye), so 1.7 code that called
 * {@code glRotatef}/{@code glTranslatef} before {@code Tessellator.addVertex} maps one to one onto
 * {@code pose().mulPose}/{@code translate} before {@link #quads}/{@link #texturedQuads}. Vertices are transformed by
 * the pose at the time of the call.
 *
 * <p>How each version draws it: 26.1 records one immediate draw through a pipeline with vanilla's
 * {@code core/position_color} or {@code core/position_tex_color} shader and the chosen blend (no depth state, as
 * vanilla's {@code CELESTIAL}); before 26.1 the same two shaders through {@code BufferUploader} with the blend set on
 * {@code RenderSystem}. Vanilla's sun and moon textures moved into the {@code celestials} atlas in 26.1;
 * {@link Texture#sun()} and {@link Texture#moon} hide that, so a quad's UVs are always 0..1 over the image.
 */
public final class Sky {

    /** The two blend functions 1.7's sky renderers used. */
    public enum Blend {
        /** {@code glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)} ({@code OpenGlHelper.glBlendFunc(770, 771, 1, 0)}). */
        ALPHA,
        /** {@code glBlendFunc(SRC_ALPHA, ONE)} ({@code OpenGlHelper.glBlendFunc(770, 1, 1, 0)}), vanilla's sun blend. */
        ADDITIVE
    }

    /** A texture for {@link #texturedQuads}: a plain image, or vanilla's sun or one moon phase. */
    public static final class Texture {
        private final String namespace;
        private final String path;
        /** -1: a plain image; -2: the sun; 0..7: that moon phase. */
        private final int celestial;

        private Texture(String namespace, String path, int celestial) {
            this.namespace = namespace;
            this.path = path;
            this.celestial = celestial;
        }

        /** A plain image, e.g. {@code of("hbm", "textures/misc/star_digamma.png")}. */
        public static Texture of(String namespace, String path) {
            return new Texture(namespace, path, -1);
        }

        private static final Texture SUN = new Texture("minecraft", "sun", -2);
        private static final Texture[] MOON = new Texture[8];

        /** Vanilla's sun image (1.7 {@code textures/environment/sun.png}). */
        public static Texture sun() {
            return SUN;
        }

        /**
         * One phase of vanilla's moon, 1.7 {@code getMoonPhase} indexing (0 full .. 4 new). UV 0..1 covers that phase's
         * cell of 1.7's {@code moon_phases.png} grid ({@code u = phase % 4 / 4}, {@code v = phase / 4 % 2 / 2}).
         */
        public static Texture moon(int phase) {
            int p = Math.floorMod(phase, 8);
            Texture t = MOON[p];
            if (t == null) MOON[p] = t = new Texture("minecraft", "moon", p);
            return t;
        }
    }

    private final ClientLevel level;
    private final PoseStack pose;
    private final float partialTick;
    private final float celestialAngle;
    private final int moonPhase;

    Sky(ClientLevel level, PoseStack pose, float partialTick, float celestialAngle, int moonPhase) {
        this.level = level;
        this.pose = pose;
        this.partialTick = partialTick;
        this.celestialAngle = celestialAngle;
        this.moonPhase = moonPhase;
    }

    public ClientLevel level() {
        return level;
    }

    /** The sky pose; see the class comment. Restore what you push. */
    public PoseStack pose() {
        return pose;
    }

    public float partialTick() {
        return partialTick;
    }

    /**
     * The angle vanilla draws this frame's sun at, as 1.7's {@code getCelestialAngle}: 0 at noon, 0.5 at midnight.
     * Before 26.1 {@code getTimeOfDay(partialTick)}; on 26.1 the interpolated {@code visual/sun_angle} / 360.
     */
    public float celestialAngle() {
        return celestialAngle;
    }

    /** The moon phase vanilla draws this frame, 1.7 {@code getMoonPhase} indexing. */
    public int moonPhase() {
        return moonPhase;
    }

    /** Untextured quads: {@code xyz} holds 3 floats per vertex, 4 vertices per quad, coloured {@code r g b a}. */
    public void quads(Blend blend, float r, float g, float b, float a, float... xyz) {
        if (xyz.length < 12) return;
        draw(null, blend, r, g, b, a, xyz, 3);
    }

    /** Textured quads: {@code xyzuv} holds 5 floats per vertex (UV 0..1 over the texture), 4 vertices per quad. */
    public void texturedQuads(Texture texture, Blend blend, float r, float g, float b, float a, float... xyzuv) {
        if (xyzuv.length < 20) return;
        draw(texture, blend, r, g, b, a, xyzuv, 5);
    }

    // ======================================================================== per-version drawing

    //? if >=26.1 {
    private static final BlendFunction BLEND_ALPHA = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
            SourceFactor.ONE, DestFactor.ZERO);
    private static final BlendFunction BLEND_ADDITIVE = BlendFunction.OVERLAY; // SRC_ALPHA, ONE, ONE, ZERO

    private static RenderPipeline pipeline(String name, boolean textured, BlendFunction blend) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation("pipeline/golem_sky_" + name)
                .withVertexShader(textured ? "core/position_tex_color" : "core/position_color")
                .withFragmentShader(textured ? "core/position_tex_color" : "core/position_color")
                .withColorTargetState(new ColorTargetState(blend))
                .withVertexFormat(textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR,
                        VertexFormat.Mode.QUADS);
        if (textured) builder.withSampler("Sampler0");
        // No depth state: neither tested nor written, as vanilla's CELESTIAL and STARS pipelines.
        return builder.build();
    }

    private static final RenderPipeline COLOR_ALPHA = pipeline("color_alpha", false, BLEND_ALPHA);
    private static final RenderPipeline COLOR_ADDITIVE = pipeline("color_additive", false, BLEND_ADDITIVE);
    private static final RenderPipeline TEX_ALPHA = pipeline("tex_alpha", true, BLEND_ALPHA);
    private static final RenderPipeline TEX_ADDITIVE = pipeline("tex_additive", true, BLEND_ADDITIVE);
    private static final RenderType TYPE_COLOR_ALPHA = RenderType.create("golem_sky_color_alpha",
            RenderSetup.builder(COLOR_ALPHA).createRenderSetup());
    private static final RenderType TYPE_COLOR_ADDITIVE = RenderType.create("golem_sky_color_additive",
            RenderSetup.builder(COLOR_ADDITIVE).createRenderSetup());
    private static final java.util.Map<String, RenderType> TEXTURED = new java.util.HashMap<>();

    private static RenderType textured(Identifier texture, Blend blend) {
        return TEXTURED.computeIfAbsent(blend + "|" + texture, key -> RenderType.create("golem_sky_tex",
                RenderSetup.builder(blend == Blend.ALPHA ? TEX_ALPHA : TEX_ADDITIVE)
                        .withTexture("Sampler0", texture).createRenderSetup()));
    }

    private void draw(Texture texture, Blend blend, float r, float g, float b, float a, float[] v, int stride) {
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        RenderType type;
        if (texture == null) {
            type = blend == Blend.ALPHA ? TYPE_COLOR_ALPHA : TYPE_COLOR_ADDITIVE;
        } else if (texture.celestial == -1) {
            type = textured(Identifier.fromNamespaceAndPath(texture.namespace, texture.path), blend);
        } else {
            var atlas = net.minecraft.client.Minecraft.getInstance().getAtlasManager()
                    .getAtlasOrThrow(net.minecraft.data.AtlasIds.CELESTIALS);
            String sprite = texture.celestial == -2 ? "sun"
                    : "moon/" + net.minecraft.world.level.MoonPhase.values()[texture.celestial].getSerializedName();
            var s = atlas.getSprite(Identifier.withDefaultNamespace(sprite));
            u0 = s.getU0();
            v0 = s.getV0();
            u1 = s.getU1();
            v1 = s.getV1();
            type = textured(atlas.location(), blend);
        }
        Matrix4f m = pose.last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                texture == null ? DefaultVertexFormat.POSITION_COLOR : DefaultVertexFormat.POSITION_TEX_COLOR);
        int quads = v.length / (stride * 4);
        for (int i = 0; i < quads * 4; i++) {
            int o = i * stride;
            var vertex = builder.addVertex(m, v[o], v[o + 1], v[o + 2]);
            if (texture != null) vertex.setUv(u0 + v[o + 3] * (u1 - u0), v0 + v[o + 4] * (v1 - v0));
            vertex.setColor(r, g, b, a);
        }
        MeshData mesh = builder.build();
        if (mesh != null) type.draw(mesh);
    }

    void end() {
    }
    //?} else {
    /*private void draw(Texture texture, Blend blend, float r, float g, float b, float a, float[] v, int stride) {
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (texture == null) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            ResourceLocation location;
            if (texture.celestial == -1) {
                location = dev.golem.core.Ids.of(texture.namespace, texture.path);
            } else if (texture.celestial == -2) {
                location = dev.golem.core.Ids.minecraft("textures/environment/sun.png");
            } else {
                location = dev.golem.core.Ids.minecraft("textures/environment/moon_phases.png");
                u0 = (texture.celestial % 4) / 4.0F;
                v0 = (texture.celestial / 4 % 2) / 2.0F;
                u1 = u0 + 0.25F;
                v1 = v0 + 0.5F;
            }
            RenderSystem.setShaderTexture(0, location);
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                blend == Blend.ALPHA ? GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA : GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        VertexFormat format = texture == null ? DefaultVertexFormat.POSITION_COLOR : DefaultVertexFormat.POSITION_TEX_COLOR;
        Matrix4f m = pose.last().pose();
        int quads = v.length / (stride * 4);
        //? if >=1.21 {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, format);
        for (int i = 0; i < quads * 4; i++) {
            int o = i * stride;
            var vertex = builder.addVertex(m, v[o], v[o + 1], v[o + 2]);
            if (texture != null) vertex.setUv(u0 + v[o + 3] * (u1 - u0), v0 + v[o + 4] * (v1 - v0));
            vertex.setColor(r, g, b, a);
        }
        com.mojang.blaze3d.vertex.MeshData mesh = builder.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        //?} else {
        /^BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, format);
        for (int i = 0; i < quads * 4; i++) {
            int o = i * stride;
            var vertex = builder.vertex(m, v[o], v[o + 1], v[o + 2]);
            if (texture != null) vertex.uv(u0 + v[o + 3] * (u1 - u0), v0 + v[o + 4] * (v1 - v0));
            vertex.color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
        ^///?}
    }

    /^* Leaves the state vanilla's sky has after its own sun, moon and stars. ^/
    void end() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }
    *///?}
}
