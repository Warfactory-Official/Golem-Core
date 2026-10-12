package dev.golem.core.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Matrix4f;
//? if >=26.1 {
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.texture.AbstractTexture;
//?} else {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.lwjgl.opengl.GL11;
*///?}

/**
 * The per-version half of {@link Sky#shaderQuads}: a mod's fragment shader over sky quads, with Golem's vertex shader
 * ({@code golemcore:core/sky_shader} on 26.1, {@code golemcore:sky_shader_legacy} before), one block of parameters and
 * up to two textures sampled with 1.7's texture-manager state (repeat, nearest).
 *
 * <ul>
 *   <li>26.1: one pipeline per shader, blend and cull ({@code MATRICES_PROJECTION_SNIPPET}, no depth state as vanilla's
 *       sky, {@code POSITION_TEX} quads, shader define {@code GOLEM_SKY_UBO}) drawn as {@code RenderType.draw} draws, with
 *       the extra uniform block {@code GolemSkyShader} (the local matrix and {@link #PARAMS} floats as {@code vec4}s) from
 *       a ring of slices written before each draw.</li>
 *   <li>1.21.1 / 1.20.1: one {@code ShaderInstance} per shader, its program JSON written here (vertex
 *       {@code golemcore:sky_shader_legacy}, the mod's fragment, uniforms {@code GolemSkyLocal} and
 *       {@code GolemSkyParams[64]}), loaded through {@link ShaderLoader}, drawn through {@code BufferUploader} with the
 *       blend set on {@code RenderSystem} after every {@code apply()} (the 1.20.1 program-blend reset, see
 *       {@link NoiseDisplacedRenderType}).</li>
 * </ul>
 */
final class SkyShaders {

    /** Floats in a shader's parameter block. */
    static final int PARAMS = 64;

    private SkyShaders() {}

    private static final Map<String, AtomicLong> DRAWS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> GUI_DRAWS = new ConcurrentHashMap<>();

    /** How many sky draws of {@code namespace:name} this client has made. */
    static long draws(String id) {
        AtomicLong count = DRAWS.get(id);
        return count == null ? 0 : count.get();
    }

    /** How many {@link GuiCanvas} draws of {@code namespace:name} this client has made. */
    static long guiDraws(String id) {
        AtomicLong count = GUI_DRAWS.get(id);
        return count == null ? 0 : count.get();
    }

    private static void count(Map<String, AtomicLong> counts, String namespace, String name) {
        counts.computeIfAbsent(namespace + ":" + name, key -> new AtomicLong()).incrementAndGet();
    }

    /** One sky draw; {@code v} holds 5 floats per vertex (position before {@code local}, UV), {@code quads} quads. */
    static void draw(String namespace, String name, int samplers, Sky.Blend blend, boolean cull, String[] textures,
                     Matrix4f local, float[] params, float[] v, int quads) {
        render(namespace, name, samplers, blend, cull, false, textures, local, params, v, quads);
        count(DRAWS, namespace, name);
    }

    /**
     * One {@link GuiCanvas} draw: as {@link #draw}, never culled; on 26.1 into the canvas' picture-in-picture texture with
     * the picture blend ({@code blend(.., true)}), before 26.1 straight onto the screen with 1.7's blend.
     */
    static void drawGui(String namespace, String name, int samplers, Sky.Blend blend, String[] textures,
                        Matrix4f local, float[] params, float[] v, int quads) {
        render(namespace, name, samplers, blend, false, true, textures, local, params, v, quads);
        count(GUI_DRAWS, namespace, name);
    }

    //? if >=26.1 {
    private static final BlendFunction BLEND_ALPHA = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
            SourceFactor.ONE, DestFactor.ZERO);
    /** 1.7's colour factors, the alpha accumulated as "over" coverage (see {@link #blend}). */
    private static final BlendFunction PICTURE_ALPHA = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
            SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA);
    /** 1.7's additive colour factors, the coverage left as it is (see {@link #blend}). */
    private static final BlendFunction PICTURE_ADDITIVE = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE,
            SourceFactor.ZERO, DestFactor.ONE);

    /**
     * The blend of a draw. In the sky ({@code picture} false): 1.7's colour factors with vanilla's alpha factors
     * {@code ONE, ZERO}. Into a GUI picture-in-picture texture ({@code picture} true), which starts transparent black and
     * is blitted with premultiplied alpha ({@code GUI_TEXTURED_PREMULTIPLIED_ALPHA}): the same colour factors, the alpha
     * kept as coverage - an alpha-blended draw adds {@code a} of it ({@code ONE, ONE_MINUS_SRC_ALPHA}), an additive one
     * none ({@code ZERO, ONE}). The texture then holds {@code (C, A)} with {@code C + (1 - A) * screen} equal to drawing
     * the same sequence straight onto the screen, as 1.7 did (up to 8-bit rounding of the texture).
     */
    static BlendFunction blend(Sky.Blend blend, boolean picture) {
        if (picture) return blend == Sky.Blend.ALPHA ? PICTURE_ALPHA : PICTURE_ADDITIVE;
        return blend == Sky.Blend.ALPHA ? BLEND_ALPHA : BlendFunction.OVERLAY;
    }

    private static final Map<String, RenderPipeline> PIPELINES = new ConcurrentHashMap<>();
    private static final int BLOCK_BYTES = 64 + PARAMS * 4;
    private static final int RING_SLOTS = 512;
    private static final java.nio.ByteBuffer BLOCK = org.lwjgl.system.MemoryUtil.memAlloc(BLOCK_BYTES);
    private static GpuBuffer ring;
    private static int slotBytes;
    private static int slot;

    private static RenderPipeline pipeline(String namespace, String name, int samplers, Sky.Blend blend, boolean cull,
                                           boolean picture) {
        String key = namespace + ":" + name + "|" + blend + "|" + cull + "|" + picture;
        return PIPELINES.computeIfAbsent(key, k -> {
            RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(dev.golem.core.Ids.of(namespace, "pipeline/golem_sky/" + name + "_"
                            + blend.name().toLowerCase(java.util.Locale.ROOT) + (cull ? "" : "_no_cull") + (picture ? "_gui" : "")))
                    .withVertexShader(dev.golem.core.Ids.of("golemcore", "core/sky_shader"))
                    .withFragmentShader(dev.golem.core.Ids.of(namespace, "core/" + name))
                    .withShaderDefine("GOLEM_SKY_UBO")
                    .withUniform("GolemSkyShader", UniformType.UNIFORM_BUFFER)
                    .withColorTargetState(new ColorTargetState(blend(blend, picture)))
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                    .withCull(cull);
            for (int i = 0; i < samplers; i++) builder.withSampler("Sampler" + i);
            // No depth state: neither tested nor written, as vanilla's sky.
            return builder.build();
        });
    }

    private static GpuBufferSlice block(Matrix4f local, float[] params) {
        com.mojang.blaze3d.systems.GpuDevice device = RenderSystem.getDevice();
        if (ring == null) {
            int alignment = Math.max(1, device.getUniformOffsetAlignment());
            slotBytes = (BLOCK_BYTES + alignment - 1) / alignment * alignment;
            ring = device.createBuffer(() -> "Golem sky shader parameters", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    (long) slotBytes * RING_SLOTS);
        }
        BLOCK.clear();
        Std140Builder std140 = Std140Builder.intoBuffer(BLOCK).putMat4f(local);
        for (int i = 0; i < PARAMS; i += 4) std140.putVec4(params[i], params[i + 1], params[i + 2], params[i + 3]);
        BLOCK.rewind();
        GpuBufferSlice slice = ring.slice((long) slot * slotBytes, BLOCK_BYTES);
        slot = (slot + 1) % RING_SLOTS;
        device.createCommandEncoder().writeToBuffer(slice, BLOCK);
        return slice;
    }

    private static void render(String namespace, String name, int samplers, Sky.Blend blend, boolean cull, boolean picture,
                               String[] textures, Matrix4f local, float[] params, float[] v, int quads) {
        RenderPipeline pipeline = pipeline(namespace, name, samplers, blend, cull, picture);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < quads * 4; i++) {
            int o = i * 5;
            builder.addVertex(v[o], v[o + 1], v[o + 2]).setUv(v[o + 3], v[o + 4]);
        }
        MeshData mesh = builder.build();
        if (mesh == null) return;
        pass(() -> "Golem sky shader " + namespace + ":" + name, pipeline, mesh, textures, samplers, block(local, params));
    }

    /**
     * Draws {@code mesh} (closed here) with {@code pipeline} into the current output target - vanilla's main target, or
     * the override a picture-in-picture renderer set - with the frame's default uniforms, an identity-coloured
     * {@code DynamicTransforms}, {@code parameters} as {@code GolemSkyShader} when not null, and {@code textures[i]} on
     * {@code Sampler<i>} with 1.7's texture-manager state (repeat, nearest).
     */
    static void pass(java.util.function.Supplier<String> label, RenderPipeline pipeline, MeshData mesh, String[] textures,
                     int samplers, GpuBufferSlice parameters) {
        try (mesh) {
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(),
                    new org.joml.Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new org.joml.Vector3f(), new Matrix4f());
            GpuBuffer vertices = pipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
            GpuBuffer indexBuffer = indices.getBuffer(mesh.drawState().indexCount());
            RenderTarget target = OutputTarget.MAIN_TARGET.getRenderTarget();
            GpuTextureView color = RenderSystem.outputColorTextureOverride != null
                    ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
            GpuTextureView depth = target.useDepth
                    ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
                    : null;
            // 1.7's texture-manager textures: GL_REPEAT, GL_NEAREST, no mipmaps
            GpuSampler sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
            // Resolve (and on first use load) every texture before the pass opens: a first load uploads the pixels through
            // the command encoder, which blaze3d refuses while a render pass is open.
            GpuTextureView[] views = new GpuTextureView[samplers];
            for (int i = 0; i < samplers; i++) {
                AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(dev.golem.core.Ids.parse(textures[i]));
                views[i] = texture.getTextureView();
            }
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    label, color, java.util.OptionalInt.empty(), depth, java.util.OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                com.mojang.blaze3d.systems.ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
                if (scissor.enabled()) pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                if (parameters != null) pass.setUniform("GolemSkyShader", parameters);
                pass.setVertexBuffer(0, vertices);
                for (int i = 0; i < samplers; i++) pass.bindTexture("Sampler" + i, views[i], sampler);
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
            }
        }
    }
    //?} else {
    /*private static final Map<String, ShaderInstance> PROGRAMS = new ConcurrentHashMap<>();
    private static Sky.Blend applying = Sky.Blend.ALPHA;

    /^* The program JSON {@code ShaderInstance} reads; the fragment is the mod's {@code <namespace>:shaders/core/<name>.fsh}. ^/
    private static String programJson(String namespace, String name, int samplers) {
        StringBuilder json = new StringBuilder();
        json.append("{\"vertex\":\"golemcore:sky_shader_legacy\",\"fragment\":\"").append(namespace).append(':').append(name)
                .append("\",\"attributes\":[\"Position\",\"UV0\"],\"samplers\":[");
        for (int i = 0; i < samplers; i++) json.append(i == 0 ? "" : ",").append("{\"name\":\"Sampler").append(i).append("\"}");
        json.append("],\"uniforms\":[");
        String identity = "[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]";
        json.append("{\"name\":\"ModelViewMat\",\"type\":\"matrix4x4\",\"count\":16,\"values\":").append(identity).append("},");
        json.append("{\"name\":\"ProjMat\",\"type\":\"matrix4x4\",\"count\":16,\"values\":").append(identity).append("},");
        json.append("{\"name\":\"GolemSkyLocal\",\"type\":\"matrix4x4\",\"count\":16,\"values\":").append(identity).append("},");
        json.append("{\"name\":\"GolemSkyParams\",\"type\":\"float\",\"count\":").append(PARAMS).append(",\"values\":[");
        for (int i = 0; i < PARAMS; i++) json.append(i == 0 ? "0.0" : ",0.0");
        json.append("]}]}");
        return json.toString();
    }

    private static ShaderInstance program(String namespace, String name, int samplers) {
        String key = namespace + ":" + name;
        ShaderInstance program = PROGRAMS.get(key);
        if (program != null) return program;
        ResourceLocation jsonId = dev.golem.core.Ids.of(namespace, "shaders/core/golem_sky/" + name + ".json");
        byte[] json = programJson(namespace, name, samplers).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ResourceProvider base = Minecraft.getInstance().getResourceManager();
        ResourceProvider provider = location -> location.equals(jsonId)
                ? java.util.Optional.of(new Resource(Minecraft.getInstance().getVanillaPackResources(),
                        () -> new java.io.ByteArrayInputStream(json)))
                : base.getResource(location);
        try {
            program = ShaderLoader.load(provider, namespace + ":golem_sky/" + name, DefaultVertexFormat.POSITION_TEX,
                    SkyShaders::applyBlend);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not load sky shader " + key, e);
        }
        PROGRAMS.put(key, program);
        return program;
    }

    private static void applyBlend() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                applying == Sky.Blend.ALPHA ? GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA : GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
    }

    /^* {@code picture} is 26.1's; before it every draw goes straight onto the screen. ^/
    private static void render(String namespace, String name, int samplers, Sky.Blend blend, boolean cull, boolean picture,
                               String[] textures, Matrix4f local, float[] params, float[] v, int quads) {
        ShaderInstance program = program(namespace, name, samplers);
        for (int i = 0; i < samplers; i++) {
            ResourceLocation id = dev.golem.core.Ids.parse(textures[i]);
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
            // 1.7's texture-manager textures: GL_REPEAT, GL_NEAREST, no mipmaps (setFilter binds the texture)
            texture.setFilter(false, false);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            RenderSystem.setShaderTexture(i, id);
        }
        Uniform localUniform = program.getUniform("GolemSkyLocal");
        if (localUniform != null) localUniform.set(new Matrix4f(local));
        Uniform paramsUniform = program.getUniform("GolemSkyParams");
        if (paramsUniform != null) paramsUniform.set(params);
        applying = blend;
        RenderSystem.setShader(() -> program);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        applyBlend();
        if (!cull) RenderSystem.disableCull();
        //? if >=1.21 {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < quads * 4; i++) {
            int o = i * 5;
            builder.addVertex(v[o], v[o + 1], v[o + 2]).setUv(v[o + 3], v[o + 4]);
        }
        com.mojang.blaze3d.vertex.MeshData mesh = builder.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        //?} else {
        /^BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < quads * 4; i++) {
            int o = i * 5;
            builder.vertex(v[o], v[o + 1], v[o + 2]).uv(v[o + 3], v[o + 4]).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
        ^///?}
        if (!cull) RenderSystem.enableCull();
    }
    *///?}
}
