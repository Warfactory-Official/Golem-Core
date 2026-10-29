package dev.golem.core.render;

import dev.golem.core.Ids;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
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
 * {@code RenderSystem}, except that textured draws use {@code golemcore:sky_tex_color_legacy}, vanilla's program with
 * 26.1's discard at alpha 0 instead of 0.1 (1.7 drew the sky without the alpha test). Vanilla's sun and moon
 * textures are in the {@code celestials} atlas on 26.1; {@link Texture#sun()} and {@link Texture#moon} hide
 * that, so a quad's UVs are always 0..1 over the image.
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

        /** {@code namespace:path} of a plain image ({@link GuiCanvas}, {@link #shaderQuads}). */
        String id() {
            return namespace + ":" + path;
        }

        /** True for a plain image, false for vanilla's sun or a moon phase. */
        boolean plain() {
            return celestial == -1;
        }

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

    /**
     * The colour this frame's sky disc is drawn with (1.7 {@code world.getSkyColor(renderViewEntity, partialTicks)}), after
     * every {@link dev.golem.core.world.Skies.Modifier}; RGB 0..1. On 26.1 the frame's extracted {@code visual/sky_color};
     * before it {@code ClientLevel.getSkyColor} at the camera.
     */
    public net.minecraft.world.phys.Vec3 skyColor() {
        //? if >=26.1 {
        int argb = Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.skyRenderState.skyColor;
        return new net.minecraft.world.phys.Vec3(((argb >> 16) & 0xFF) / 255.0, ((argb >> 8) & 0xFF) / 255.0, (argb & 0xFF) / 255.0);
        //?} else {
        /*Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return level.getSkyColor(camera, partialTick);
        *///?}
    }

    /** The current camera's sampled fog colour. */
    public Vec3 fogColor() {
        //? if >=26.1 {
        int color = Minecraft.getInstance().gameRenderer.getMainCamera().attributeProbe()
                .getValue(net.minecraft.world.attribute.EnvironmentAttributes.FOG_COLOR, partialTick);
        return new Vec3(net.minecraft.util.ARGB.redFloat(color),
                net.minecraft.util.ARGB.greenFloat(color), net.minecraft.util.ARGB.blueFloat(color));
        //?} else {
        /*var color = RenderSystem.getShaderFogColor();
        return new Vec3(color[0], color[1], color[2]);
        *///?}
    }
    /** Untextured quads: {@code xyz} holds 3 floats per vertex, 4 vertices per quad, coloured {@code r g b a}. */
    public void quads(Blend blend, float r, float g, float b, float a, float... xyz) {
        if (xyz.length < 12) return;
        draw(null, blend, r, g, b, a, xyz, 3);
    }

    /**
     * Untextured quads with a colour per vertex, interpolated across each quad (1.7's {@code GL_SMOOTH} shading):
     * {@code xyzrgba} holds 7 floats per vertex, 4 vertices per quad. A 1.7 triangle fan (centre, then rim) becomes one
     * quad per rim segment, {@code (centre, rim[j], rim[j + 1], centre)}, which keeps the fan's winding.
     */
    public void coloredQuads(Blend blend, float... xyzrgba) {
        if (xyzrgba.length < 28) return;
        draw(null, blend, 1.0F, 1.0F, 1.0F, 1.0F, xyzrgba, 7);
    }

    /** Textured quads: {@code xyzuv} holds 5 floats per vertex (UV 0..1 over the texture), 4 vertices per quad. */
    public void texturedQuads(Texture texture, Blend blend, float r, float g, float b, float a, float... xyzuv) {
        if (xyzuv.length < 20) return;
        draw(texture, blend, r, g, b, a, xyzuv, 5);
    }

    /**
     * One-pixel lines (1.7 {@code GL_LINES} / {@code GL_LINE_LOOP} at line width 1): {@code xyz} holds 3 floats per
     * vertex; with {@code loop} the vertices are one closed line loop, else consecutive pairs are separate segments.
     */
    public void lines(Blend blend, float r, float g, float b, float a, boolean loop, float... xyz) {
        int n = xyz.length / 3;
        if (n < 2) return;
        float[] v;
        if (loop) {
            v = new float[n * 2 * 3];
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                System.arraycopy(xyz, i * 3, v, i * 6, 3);
                System.arraycopy(xyz, j * 3, v, i * 6 + 3, 3);
            }
        } else {
            v = java.util.Arrays.copyOf(xyz, (n / 2) * 6);
        }
        drawLines(null, blend, r, g, b, a, v, 3);
    }

    /**
     * Points one pixel across, sampling UV (0, 0) when textured. On 26.1 the native POINTS topology and an explicit
     * shader point size preserve one vertex per point; older clients use one-pixel camera-facing line segments.
     */
    public void points(Texture texture, Blend blend, float r, float g, float b, float a, float... xyz) {
        int n = xyz.length / 3;
        if (n < 1) return;
        //? if >=26.1 {
        drawPoints(texture, blend, r, g, b, a, xyz);
        //?} else {
        /*
        Matrix4f m = pose.last().pose();
        org.joml.Vector3f right = new org.joml.Matrix3f(m).invert().transform(new org.joml.Vector3f(1, 0, 0));
        if (right.lengthSquared() > 0) right.normalize();
        int height = Math.max(1, Minecraft.getInstance().getWindow().getHeight());
        double fov = dev.golem.core.client.Fov.current();
        float perDistance = (float) (2.0 * Math.tan(Math.toRadians(fov) / 2.0) / height);
        float[] v = new float[n * 2 * 5];
        for (int i = 0; i < n; i++) {
            float x = xyz[i * 3], y = xyz[i * 3 + 1], z = xyz[i * 3 + 2];
            float px = perDistance * (float) Math.sqrt(x * x + y * y + z * z);
            int o = i * 10;
            v[o] = x; v[o + 1] = y; v[o + 2] = z; v[o + 3] = 0; v[o + 4] = 0;
            v[o + 5] = x + right.x * px; v[o + 6] = y + right.y * px; v[o + 7] = z + right.z * px; v[o + 8] = 0; v[o + 9] = 0;
        }
        drawLines(texture, blend, r, g, b, a, v, 5);
        *///?}
    }

    // ======================================================================== mod shaders

    /**
     * A mod's fragment shader for {@link #shaderQuads}, the stand-in for 1.7's {@code Shader.use()} around a sky quad
     * (hbm-space's planet clouds, city lights, black-hole sun...). The file is {@code <namespace>:shaders/core/<name>.fsh},
     * one file for every node, in GLSL 1.50:
     *
     * <pre>
     * #version 150
     * #ifdef GOLEM_SKY_UBO
     * layout(std140) uniform GolemSkyShader { mat4 GolemSkyLocal; vec4 GolemSkyParams[16]; };
     * #define PARAM(i) GolemSkyParams[(i) / 4][(i) % 4]
     * #else
     * uniform float GolemSkyParams[64];
     * #define PARAM(i) GolemSkyParams[i]
     * #endif
     * uniform sampler2D Sampler0;    // only as many as the shader declares (up to Sampler2)
     * in vec2 texCoord0;             // 1.7 gl_TexCoord[0].xy
     * in vec3 localPos;              // the position as submitted (1.7 gl_Vertex, hbm-space default.vert's vPosition)
     * out vec4 fragColor;
     * </pre>
     * <p>
     * {@code PARAM(0..63)} are the call's parameters (1.7's {@code setUniform} values, an int as a float). The vertex
     * half, the matrices and the program are Golem's ({@code golemcore:core/sky_shader} on 26.1, where
     * {@code GOLEM_SKY_UBO} is defined; {@code golemcore:sky_shader_legacy} and a generated program JSON before).
     */
    public static final class Shader {
        private final String namespace;
        private final String name;
        private final int samplers;
        private final boolean premultipliedAlpha;

        private Shader(String namespace, String name, int samplers, boolean premultipliedAlpha) {
            this.namespace = namespace;
            this.name = name;
            this.samplers = samplers;
            this.premultipliedAlpha = premultipliedAlpha;
        }

        /**
         * The shader {@code <namespace>:shaders/core/<name>.fsh}, which samples {@code Sampler0 .. Sampler<samplers - 1>}
         * (0 to 3).
         */
        public static Shader of(String namespace, String name, int samplers) {
            if (samplers < 0 || samplers > 3) throw new IllegalArgumentException("a sky shader samples 0 to 3 textures");
            return new Shader(namespace, name, samplers, false);
        }

        /** RGB already contains emission; alpha describes how much background the fragment occludes. */
        public Shader premultipliedAlpha() {
            return new Shader(namespace, name, samplers, true);
        }

        boolean usesPremultipliedAlpha() {
            return premultipliedAlpha;
        }

        //? if >=26.1 {
        /** The native pipeline used by this shader's sky draws, including its actual uniforms and blend state. */
        public RenderPipeline nativePipeline(Blend blend, boolean cull) {
            return SkyShaders.pipeline(namespace, name, samplers, blend, cull, false, premultipliedAlpha);
        }
        //?}

        String namespace() {
            return namespace;
        }

        String name() {
            return name;
        }

        int samplers() {
            return samplers;
        }

        /** {@code namespace:name}. */
        public String id() {
            return namespace + ":" + name;
        }
    }

    /** How many floats a {@link #shaderQuads} parameter block has ({@code PARAM(0)} to {@code PARAM(63)}). */
    public static final int SHADER_PARAMS = SkyShaders.PARAMS;

    /**
     * Quads drawn by a mod's {@link Shader}: {@code xyzuv} holds 5 floats per vertex, 4 vertices per quad, in the
     * current pose (the shader sees the position as given in {@code localPos}); {@code params} up to
     * {@link #SHADER_PARAMS} floats (missing ones are 0); {@code textures} one plain image per sampler the shader declares,
     * sampled with repeat and nearest filtering as 1.7's texture manager left them. No depth test or write and no fog,
     * as every sky draw; {@code cull} false is 1.7's {@code glDisable(GL_CULL_FACE)}. The fragment's colour is blended as
     * it is: the shader decides the alpha (1.7's sky shaders ignored {@code glColor}).
     */
    public void shaderQuads(Shader shader, Blend blend, boolean cull, float[] params, Texture[] textures, float... xyzuv) {
        int quads = xyzuv.length / 20;
        if (quads < 1) return;
        String[] ids = new String[shader.samplers];
        for (int i = 0; i < shader.samplers; i++) {
            Texture texture = textures[i];
            if (texture.celestial != -1) throw new IllegalArgumentException("sky shaders sample plain images only");
            ids[i] = texture.namespace + ":" + texture.path;
        }
        float[] block = java.util.Arrays.copyOf(params, SHADER_PARAMS);
        SkyShaders.draw(shader.namespace, shader.name, shader.samplers, blend, cull, shader.premultipliedAlpha, ids, new Matrix4f(pose.last().pose()),
                block, xyzuv, quads);
    }

    /** How many {@link #shaderQuads} draws of {@code shader} this client has made (a positive signal for tests). */
    public static long shaderDraws(Shader shader) {
        return SkyShaders.draws(shader.id());
    }

    // ======================================================================== per-version drawing

    //? if >=26.1 {
    private static final BlendFunction BLEND_ALPHA = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
            SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
    private static final BlendFunction BLEND_ADDITIVE = BlendFunction.OVERLAY; // SRC_ALPHA, ONE, ONE, ZERO

    private static RenderPipeline pipeline(String name, boolean textured, BlendFunction blend) {
        return pipeline(name, textured, blend, VertexFormat.Mode.QUADS);
    }

    private static RenderPipeline pipeline(String name, boolean textured, BlendFunction blend, VertexFormat.Mode mode) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation("pipeline/golem_sky_" + name)
                .withVertexShader(Ids.parse(mode == VertexFormat.Mode.POINTS ? "golemcore:core/sky_points"
                        : textured ? "core/position_tex_color" : "core/position_color"))
                .withFragmentShader(textured ? "core/position_tex_color" : "core/position_color")
                .withColorTargetState(new ColorTargetState(blend))
                .withVertexFormat(textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR, mode);
        if (textured) builder.withSampler("Sampler0");
        if (textured && mode == VertexFormat.Mode.POINTS) builder.withShaderDefine("TEXTURED");
        // No depth state: neither tested nor written, as vanilla's CELESTIAL and STARS pipelines.
        return WorldPipelines.add(builder.build());
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
    private static final RenderPipeline LINE_COLOR_ALPHA = pipeline("line_color_alpha", false, BLEND_ALPHA, VertexFormat.Mode.DEBUG_LINES);
    private static final RenderPipeline LINE_COLOR_ADDITIVE = pipeline("line_color_additive", false, BLEND_ADDITIVE, VertexFormat.Mode.DEBUG_LINES);
    private static final RenderPipeline LINE_TEX_ALPHA = pipeline("line_tex_alpha", true, BLEND_ALPHA, VertexFormat.Mode.DEBUG_LINES);
    private static final RenderPipeline LINE_TEX_ADDITIVE = pipeline("line_tex_additive", true, BLEND_ADDITIVE, VertexFormat.Mode.DEBUG_LINES);
    private static final RenderType TYPE_LINE_COLOR_ALPHA = RenderType.create("golem_sky_line_color_alpha",
            RenderSetup.builder(LINE_COLOR_ALPHA).createRenderSetup());
    private static final RenderType TYPE_LINE_COLOR_ADDITIVE = RenderType.create("golem_sky_line_color_additive",
            RenderSetup.builder(LINE_COLOR_ADDITIVE).createRenderSetup());
    private static final java.util.Map<String, RenderType> TEXTURED_LINES = new java.util.HashMap<>();

    private static RenderType texturedLines(Identifier texture, Blend blend) {
        return TEXTURED_LINES.computeIfAbsent(blend + "|" + texture, key -> RenderType.create("golem_sky_line_tex",
                RenderSetup.builder(blend == Blend.ALPHA ? LINE_TEX_ALPHA : LINE_TEX_ADDITIVE)
                        .withTexture("Sampler0", texture).createRenderSetup()));
    }

    private static final RenderPipeline POINT_COLOR_ALPHA = pipeline("point_color_alpha", false, BLEND_ALPHA, VertexFormat.Mode.POINTS);
    private static final RenderPipeline POINT_COLOR_ADDITIVE = pipeline("point_color_additive", false, BLEND_ADDITIVE, VertexFormat.Mode.POINTS);
    private static final RenderPipeline POINT_TEX_ALPHA = pipeline("point_tex_alpha", true, BLEND_ALPHA, VertexFormat.Mode.POINTS);
    private static final RenderPipeline POINT_TEX_ADDITIVE = pipeline("point_tex_additive", true, BLEND_ADDITIVE, VertexFormat.Mode.POINTS);
    private static final RenderType TYPE_POINT_COLOR_ALPHA = RenderType.create("golem_sky_point_color_alpha",
            RenderSetup.builder(POINT_COLOR_ALPHA).createRenderSetup());
    private static final RenderType TYPE_POINT_COLOR_ADDITIVE = RenderType.create("golem_sky_point_color_additive",
            RenderSetup.builder(POINT_COLOR_ADDITIVE).createRenderSetup());
    private static final Map<String, RenderType> TEXTURED_POINTS = new HashMap<>();

    /** Actual native pipelines shared by the renderer's draws and consumers inspecting their state. */
    public static RenderPipeline nativePipeline(boolean textured, Blend blend, VertexFormat.Mode mode) {
        return switch (mode) {
            case QUADS -> textured ? (blend == Blend.ALPHA ? TEX_ALPHA : TEX_ADDITIVE)
                    : (blend == Blend.ALPHA ? COLOR_ALPHA : COLOR_ADDITIVE);
            case DEBUG_LINES -> textured ? (blend == Blend.ALPHA ? LINE_TEX_ALPHA : LINE_TEX_ADDITIVE)
                    : (blend == Blend.ALPHA ? LINE_COLOR_ALPHA : LINE_COLOR_ADDITIVE);
            case POINTS -> textured ? (blend == Blend.ALPHA ? POINT_TEX_ALPHA : POINT_TEX_ADDITIVE)
                    : (blend == Blend.ALPHA ? POINT_COLOR_ALPHA : POINT_COLOR_ADDITIVE);
            default -> throw new IllegalArgumentException("Sky does not draw " + mode);
        };
    }

    /** Plain images only, as with the sky's line draws. */
    private void drawPoints(Texture texture, Blend blend, float r, float g, float b, float a, float[] xyz) {
        var type = texture == null ? (blend == Blend.ALPHA ? TYPE_POINT_COLOR_ALPHA : TYPE_POINT_COLOR_ADDITIVE)
                : TEXTURED_POINTS.computeIfAbsent(blend + "|" + texture.id(), key -> RenderType.create("golem_sky_point_tex",
                        RenderSetup.builder(nativePipeline(true, blend, VertexFormat.Mode.POINTS))
                                .withTexture("Sampler0", Identifier.fromNamespaceAndPath(texture.namespace, texture.path))
                                .createRenderSetup()));
        drawVertices(type, VertexFormat.Mode.POINTS, texture, r, g, b, a, xyz, 3, xyz.length / 3, 0, 0, 1, 1);
    }

    /** Plain images only. */
    private void drawLines(Texture texture, Blend blend, float r, float g, float b, float a, float[] v, int stride) {
        var type = texture == null ? (blend == Blend.ALPHA ? TYPE_LINE_COLOR_ALPHA : TYPE_LINE_COLOR_ADDITIVE)
                : texturedLines(Identifier.fromNamespaceAndPath(texture.namespace, texture.path), blend);
        drawVertices(type, VertexFormat.Mode.DEBUG_LINES, texture, r, g, b, a, v, stride,
                (v.length / (stride * 2)) * 2, 0, 0, 1, 1);
    }

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
            var atlas = Minecraft.getInstance().getAtlasManager()
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
        drawVertices(type, VertexFormat.Mode.QUADS, texture, r, g, b, a, v, stride,
                v.length / (stride * 4) * 4, u0, v0, u1, v1);
    }

    private void drawVertices(RenderType type, VertexFormat.Mode mode, Texture texture, float r, float g, float b, float a,
                              float[] vertices, int stride, int count, float u0, float v0, float u1, float v1) {
        var matrix = pose.last().pose();
        var builder = Tesselator.getInstance().begin(mode,
                texture == null ? DefaultVertexFormat.POSITION_COLOR : DefaultVertexFormat.POSITION_TEX_COLOR);
        for (var i = 0; i < count; i++) {
            var offset = i * stride;
            var vertex = builder.addVertex(matrix, vertices[offset], vertices[offset + 1], vertices[offset + 2]);
            if (texture != null) vertex.setUv(stride == 3 ? 0 : u0 + vertices[offset + 3] * (u1 - u0),
                    stride == 3 ? 0 : v0 + vertices[offset + 4] * (v1 - v0));
            if (stride == 7) vertex.setColor(vertices[offset + 3], vertices[offset + 4], vertices[offset + 5], vertices[offset + 6]);
            else vertex.setColor(r, g, b, a);
        }
        var mesh = builder.build();
        if (mesh != null) type.draw(mesh);
    }

    void end() {
    }
    //?} else {
    /*private static final String TEX_SHADER = "golemcore:sky_tex_color_legacy";
    private static net.minecraft.client.renderer.ShaderInstance texShader;
    private static Blend texBlend = Blend.ALPHA;
    private static boolean texReloadRegistered;

    /^*
     * The textured program: vanilla's {@code position_tex_color} with the discard at alpha 0 instead of 0.1, as 26.1's
     * own {@code core/position_tex_color} and 1.7's sky (drawn with {@code GL_ALPHA_TEST} disabled). With vanilla's
     * 1.20.1/1.21.1 program every texel below alpha 0.1 vanished: a star's soft halo became a hard-edged disc and a
     * flare drawn at a quarter alpha lost its faint parts. Its {@code apply()} resets the blend (the program's JSON
     * states none on purpose, see {@code FaintParticleLayers}), so the hook sets this draw's blend again.
     ^/
    private static net.minecraft.client.renderer.ShaderInstance texShader() {
        if (!texReloadRegistered) {
            texReloadRegistered = true;
            dev.golem.core.client.ClientResourceReloads.register("golemcore", "sky_tex_color", () -> {
                if (texShader != null) texShader.close();
                texShader = null;
            });
        }
        if (texShader != null) return texShader;
        try {
            texShader = ShaderLoader.load(Minecraft.getInstance().getResourceManager(), TEX_SHADER,
                    DefaultVertexFormat.POSITION_TEX_COLOR, () -> blend(texBlend));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not load shader " + TEX_SHADER, e);
        }
        return texShader;
    }

    private static void blend(Blend blend) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                blend == Blend.ALPHA ? GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA : GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
    }

    private void draw(Texture texture, Blend blend, float r, float g, float b, float a, float[] v, int stride) {
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (texture == null) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        } else {
            texBlend = blend;
            RenderSystem.setShader(Sky::texShader);
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
            if (stride == 7) vertex.setColor(v[o + 3], v[o + 4], v[o + 5], v[o + 6]);
            else vertex.setColor(r, g, b, a);
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
            if (stride == 7) vertex.color(v[o + 3], v[o + 4], v[o + 5], v[o + 6]).endVertex();
            else vertex.color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
        ^///?}
    }

    /^* {@link #lines} / {@link #points}: plain images only. ^/
    private void drawLines(Texture texture, Blend blend, float r, float g, float b, float a, float[] v, int stride) {
        if (texture == null) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        } else {
            texBlend = blend;
            RenderSystem.setShader(Sky::texShader);
            RenderSystem.setShaderTexture(0, dev.golem.core.Ids.of(texture.namespace, texture.path));
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                blend == Blend.ALPHA ? GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA : GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.lineWidth(1.0F);
        VertexFormat format = texture == null ? DefaultVertexFormat.POSITION_COLOR : DefaultVertexFormat.POSITION_TEX_COLOR;
        Matrix4f m = pose.last().pose();
        int count = (v.length / (stride * 2)) * 2;
        //? if >=1.21 {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, format);
        for (int i = 0; i < count; i++) {
            int o = i * stride;
            var vertex = builder.addVertex(m, v[o], v[o + 1], v[o + 2]);
            if (texture != null) vertex.setUv(v[o + 3], v[o + 4]);
            vertex.setColor(r, g, b, a);
        }
        com.mojang.blaze3d.vertex.MeshData mesh = builder.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        //?} else {
        /^BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.DEBUG_LINES, format);
        for (int i = 0; i < count; i++) {
            int o = i * stride;
            var vertex = builder.vertex(m, v[o], v[o + 1], v[o + 2]);
            if (texture != null) vertex.uv(v[o + 3], v[o + 4]);
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
