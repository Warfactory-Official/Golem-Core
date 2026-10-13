package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
//? if >=26.1 {
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
//?} else {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
*///?}

/**
 * Immediate-mode drawing inside a screen rectangle, the way a 1.7 {@code GuiScreen} bound a texture, set
 * {@code glBlendFunc} and {@code glColor} and tesselated quads - including quads drawn by a mod's fragment shader with
 * per-draw parameters (1.7 {@code Shader.use()} plus {@code setUniform}), which the 26.1 GUI's batched element pipeline
 * cannot express.
 *
 * <p>{@link #draw} hands the painter a canvas whose {@link #pose()} is in GUI pixels of the screen, +Y down, the same
 * coordinates the rectangle is given in (the GUI's own pose is not applied, as {@link dev.golem.core.gui.GuiScenes}).
 * Every call records its quads, transformed by the pose at the time of the call; the record is drawn in call order,
 * clipped to the rectangle, never depth-tested or culled (a 1.7 GUI quad faces the viewer either way), with the blend
 * of each call:
 * <ul>
 *   <li>{@link #quads}, {@link #coloredQuads} (a colour per vertex, 1.7 {@code GL_SMOOTH}: gradient bands) and
 *       {@link #texturedQuads}: vanilla's {@code position_color} / {@code position_tex_color} shaders, a plain image sampled
 *       with 1.7's texture-manager state (repeat, nearest) so a UV beyond 0..1 wraps as it did.</li>
 *   <li>{@link #shaderQuads}: a {@link Sky.Shader}, the same fragment files and parameter block as the sky's
 *       {@link Sky#shaderQuads}; up to three plain images.</li>
 * </ul>
 *
 * <p>26.1 renders the record into a picture-in-picture texture through {@link dev.golem.core.gui.GuiScenes} (one
 * offscreen draw per call, with its own pipeline and, for a shader, its own parameter slice) and the GUI blits that
 * texture in the screen's order. The draws use 1.7's colour blend factors and keep the alpha as coverage
 * ({@code SkyShaders.blend(.., true)}), so the premultiplied blit matches drawing the sequence straight onto the
 * screen. Before 26.1 the GUI is flushed and the record is drawn onto the screen with 1.7's blend through
 * {@code BufferUploader} ({@code ShaderInstance} programs for shaders).
 *
 * <p>Client only.
 */
public final class GuiCanvas {

    private static final AtomicLong CANVASES = new AtomicLong();

    private final PoseStack pose = new PoseStack();
    private final List<Op> ops = new ArrayList<>();

    private GuiCanvas() {}

    /** One recorded call: plain geometry ({@code shader} null) or a shader draw. */
    private record Op(Sky.Blend blend, String texture, float[] v, int vertices,
                      Sky.Shader shader, String[] textures, Matrix4f local, float[] params) {}

    /**
     * Draws what {@code painter} records into the screen rectangle {@code (x0, y0) - (x1, y1)} (GUI pixels), in the
     * screen's drawing order at this call. The painter runs at once, before this returns.
     */
    public static void draw(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            int x0, int y0, int x1, int y1, Consumer<GuiCanvas> painter) {
        if (x1 <= x0 || y1 <= y0) return;
        GuiCanvas canvas = new GuiCanvas();
        painter.accept(canvas);
        if (canvas.ops.isEmpty()) return;
        //? if >=26.1 {
        float cx = (x0 + x1) / 2.0F, cy = (y0 + y1) / 2.0F;
        dev.golem.core.gui.GuiScenes.draw(graphics, x0, y0, x1, y1, (pose, sink, camera) -> {
            // GuiScenes' frame: origin at the rectangle's centre, GUI pixels, +Y down
            canvas.replay(new Matrix4f(pose.last().pose()).translate(-cx, -cy, 0.0F));
        });
        //?} else {
        /*graphics.flush();
        graphics.enableScissor(x0, y0, x1, y1);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        canvas.replay(new Matrix4f(graphics.pose().last().pose()));
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        graphics.disableScissor();
        *///?}
    }

    /** The canvas pose: screen GUI pixels, +Y down. Restore what you push. */
    public PoseStack pose() {
        return pose;
    }

    /** Untextured quads: {@code xyz} holds 3 floats per vertex, 4 vertices per quad, coloured {@code r g b a}. */
    public void quads(Sky.Blend blend, float r, float g, float b, float a, float... xyz) {
        int n = xyz.length / 3 / 4 * 4;
        if (n < 4) return;
        float[] v = new float[n * 9];
        for (int i = 0; i < n; i++) put(v, i, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2], 0, 0, r, g, b, a);
        ops.add(new Op(blend, null, v, n, null, null, null, null));
    }

    /**
     * Untextured quads with a colour per vertex, interpolated across each quad (1.7 {@code GL_SMOOTH}):
     * {@code xyzrgba} holds 7 floats per vertex, 4 vertices per quad.
     */
    public void coloredQuads(Sky.Blend blend, float... xyzrgba) {
        int n = xyzrgba.length / 7 / 4 * 4;
        if (n < 4) return;
        float[] v = new float[n * 9];
        for (int i = 0; i < n; i++) {
            int o = i * 7;
            put(v, i, xyzrgba[o], xyzrgba[o + 1], xyzrgba[o + 2], 0, 0, xyzrgba[o + 3], xyzrgba[o + 4], xyzrgba[o + 5], xyzrgba[o + 6]);
        }
        ops.add(new Op(blend, null, v, n, null, null, null, null));
    }

    /**
     * Textured quads: {@code xyzuv} holds 5 floats per vertex, 4 vertices per quad, tinted {@code r g b a}; a plain image
     * (not vanilla's sun or moon), repeated beyond UV 0..1, nearest filtering.
     */
    public void texturedQuads(Sky.Texture texture, Sky.Blend blend, float r, float g, float b, float a, float... xyzuv) {
        if (!texture.plain()) throw new IllegalArgumentException("a GUI canvas draws plain images only");
        int n = xyzuv.length / 5 / 4 * 4;
        if (n < 4) return;
        float[] v = new float[n * 9];
        for (int i = 0; i < n; i++) {
            int o = i * 5;
            put(v, i, xyzuv[o], xyzuv[o + 1], xyzuv[o + 2], xyzuv[o + 3], xyzuv[o + 4], r, g, b, a);
        }
        ops.add(new Op(blend, texture.id(), v, n, null, null, null, null));
    }

    /**
     * Quads drawn by {@code shader} (see {@link Sky.Shader}): {@code xyzuv} holds 5 floats per vertex, 4 vertices per
     * quad, in the current pose (the shader sees the position as given in {@code localPos}); {@code params} up to
     * {@link Sky#SHADER_PARAMS} floats (missing ones are 0); {@code textures} one plain image per sampler the shader
     * declares. The fragment's colour is blended as it is: the shader decides the alpha.
     */
    public void shaderQuads(Sky.Shader shader, Sky.Blend blend, float[] params, Sky.Texture[] textures, float... xyzuv) {
        int quads = xyzuv.length / 20;
        if (quads < 1) return;
        String[] ids = new String[shader.samplers()];
        for (int i = 0; i < ids.length; i++) {
            if (!textures[i].plain()) throw new IllegalArgumentException("GUI shaders sample plain images only");
            ids[i] = textures[i].id();
        }
        ops.add(new Op(blend, null, java.util.Arrays.copyOf(xyzuv, quads * 20), quads * 4, shader, ids,
                new Matrix4f(pose.last().pose()), java.util.Arrays.copyOf(params, Sky.SHADER_PARAMS)));
    }

    /** How many {@link #shaderQuads} draws of {@code shader} this client has made (a positive signal for tests). */
    public static long shaderDraws(Sky.Shader shader) {
        return SkyShaders.guiDraws(shader.id());
    }

    /** How many non-empty canvases this client has drawn. */
    public static long canvasesDrawn() {
        return CANVASES.get();
    }

    /** Vertex {@code i} of a plain op: position (posed now), UV, colour. */
    private void put(float[] v, int i, float x, float y, float z, float u, float tv, float r, float g, float b, float a) {
        Vector3f p = pose.last().pose().transformPosition(x, y, z, new Vector3f());
        int o = i * 9;
        v[o] = p.x;
        v[o + 1] = p.y;
        v[o + 2] = p.z;
        v[o + 3] = u;
        v[o + 4] = tv;
        v[o + 5] = r;
        v[o + 6] = g;
        v[o + 7] = b;
        v[o + 8] = a;
    }

    /** Draws the record, every position mapped by {@code base} (canvas pixels to the target's frame). */
    private void replay(Matrix4f base) {
        CANVASES.incrementAndGet();
        Vector3f p = new Vector3f();
        for (Op op : ops) {
            if (op.shader != null) {
                Sky.Shader s = op.shader;
                SkyShaders.drawGui(s.namespace(), s.name(), s.samplers(), op.blend, op.textures,
                        new Matrix4f(base).mul(op.local), op.params, op.v, op.vertices / 4);
            } else {
                drawPlain(base, op, p);
            }
        }
    }

    //? if >=26.1 {
    private static final java.util.Map<String, RenderPipeline> PIPELINES = new java.util.concurrent.ConcurrentHashMap<>();

    private static RenderPipeline pipeline(boolean textured, Sky.Blend blend) {
        return PIPELINES.computeIfAbsent(textured + "|" + blend, key -> {
            RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(dev.golem.core.Ids.of("golemcore", "pipeline/gui_canvas_" + (textured ? "tex_" : "color_")
                            + blend.name().toLowerCase(java.util.Locale.ROOT)))
                    .withVertexShader(textured ? "core/position_tex_color" : "core/position_color")
                    .withFragmentShader(textured ? "core/position_tex_color" : "core/position_color")
                    .withColorTargetState(new ColorTargetState(SkyShaders.blend(blend, true)))
                    .withVertexFormat(textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR,
                            VertexFormat.Mode.QUADS)
                    .withCull(false);
            if (textured) builder.withSampler("Sampler0");
            // No depth state: a canvas is drawn in submission order, as a 1.7 GUI.
            return builder.build();
        });
    }

    private static void drawPlain(Matrix4f base, Op op, Vector3f p) {
        boolean textured = op.texture != null;
        RenderPipeline pipeline = pipeline(textured, op.blend);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR);
        float[] v = op.v;
        for (int i = 0; i < op.vertices; i++) {
            int o = i * 9;
            base.transformPosition(v[o], v[o + 1], v[o + 2], p);
            var vertex = builder.addVertex(p.x, p.y, p.z);
            if (textured) vertex.setUv(v[o + 3], v[o + 4]);
            vertex.setColor(v[o + 5], v[o + 6], v[o + 7], v[o + 8]);
        }
        MeshData mesh = builder.build();
        if (mesh == null) return;
        SkyShaders.pass(() -> "Golem GUI canvas", pipeline, mesh, textured ? new String[] {op.texture} : new String[0],
                textured ? 1 : 0, null);
    }
    //?} else {
    /*private static void drawPlain(Matrix4f base, Op op, Vector3f p) {
        boolean textured = op.texture != null;
        if (textured) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            ResourceLocation id = dev.golem.core.Ids.parse(op.texture);
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
            // 1.7's texture-manager textures: GL_REPEAT, GL_NEAREST, no mipmaps (setFilter binds the texture)
            texture.setFilter(false, false);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            RenderSystem.setShaderTexture(0, id);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                op.blend == Sky.Blend.ALPHA ? GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA : GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        VertexFormat format = textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR;
        float[] v = op.v;
        //? if >=1.21 {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, format);
        for (int i = 0; i < op.vertices; i++) {
            int o = i * 9;
            base.transformPosition(v[o], v[o + 1], v[o + 2], p);
            var vertex = builder.addVertex(p.x, p.y, p.z);
            if (textured) vertex.setUv(v[o + 3], v[o + 4]);
            vertex.setColor(v[o + 5], v[o + 6], v[o + 7], v[o + 8]);
        }
        com.mojang.blaze3d.vertex.MeshData mesh = builder.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        //?} else {
        /^BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, format);
        for (int i = 0; i < op.vertices; i++) {
            int o = i * 9;
            base.transformPosition(v[o], v[o + 1], v[o + 2], p);
            var vertex = builder.vertex(p.x, p.y, p.z);
            if (textured) vertex.uv(v[o + 3], v[o + 4]);
            vertex.color(v[o + 5], v[o + 6], v[o + 7], v[o + 8]).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
        ^///?}
    }
    *///?}
}
