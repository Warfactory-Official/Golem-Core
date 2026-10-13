package dev.golem.core.render;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.golem.core.GolemCore;
import dev.golem.core.client.ClientResourceReloads;
import dev.golem.core.harness.HarnessQueryRegistry;
import dev.golem.core.platform.Platform;
import dev.golem.core.render.gem.Geometry;
import dev.golem.core.render.gem.Surface;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Static geometry kept on the GPU: a lump of triangles is uploaded once, in its own model space, and every frame is
 * one draw with that frame's pose, light, overlay and tint, instead of the whole lump being transformed and written
 * vertex by vertex on the CPU.
 *
 * <p><b>The output is the immediate draw's.</b> The geometry still goes through {@link SubmitSink#submitCustomGeometry}
 * with the same render type, so it is drawn at the same point of the frame, into the same target, with the same
 * projection, model-view, lights, fog and textures as the batch it would have joined. Only the per-vertex arithmetic
 * moves: the vertex shader does what {@code VertexConsumer.addVertex(pose, ...)}, {@code setNormal(pose, ...)} and
 * {@code BufferBuilder.setNormal} do (the pose, the normal matrix, the normalisation, the byte quantisation of the
 * normal), then runs the vanilla entity shader body. The fragment stage is vanilla's own. The mesh is captured from the
 * geometry's own {@link Geometry#draw} with an identity pose, so the GPU draws the same quads in the same order.
 *
 * <p>Floating point is matched too. The shaders spell out JOML's operation order and mark it {@code precise}, so the
 * GPU neither fuses a multiply and an add nor reorders them, and they take the normal's root and quotient in doubles
 * where the GPU has them, because the JVM rounds those exactly and a GPU's float sqrt and divide do not. Without
 * that, edge pixels differ (a fused multiply-add moves a vertex across a pixel centre).
 *
 * <p>It falls back to the immediate path, per draw, whenever it cannot be equivalent: a surface other than an
 * alpha-tested entity surface, a geometry whose colour, light or overlay does not pass straight through, a buffer
 * that is not a plain {@code BufferBuilder} or Embeddium's writer for one (the glowing outline wraps it), a
 * shader-pack pipeline (Iris, Oculus), after any failure (one warning, then off for the session), and while switched off with
 * {@code -Dgolem.staticMeshes=false} or the harness query {@code golem_static_meshes mode=off}. It has nothing to do
 * with Flywheel and works the same whether the instancing engine is on, off or absent.
 *
 * <p>A mesh is keyed by the {@link Geometry} instance, released when the client resources reload, and released when
 * it has not been drawn for {@value #IDLE_SECONDS} seconds (a model rebuilt for a new world leaves its old lumps
 * behind).
 */
public final class StaticMeshes {

    private static final int IDLE_SECONDS = 30;
    // Capture sentinels: a geometry qualifies only if it writes exactly what it is given.
    private static final int LIGHT_A = 0x00A50070;
    private static final int OVERLAY_A = 0x00070003;
    private static final int LIGHT_B = 0x00300010;
    private static final int OVERLAY_B = 0x000A0005;
    private static final int COLOR_B = 0x80C04020;

    private static boolean initialised;
    private static boolean enabled = !"false".equalsIgnoreCase(System.getProperty("golem.staticMeshes", "true"));
    private static boolean failed;
    private static @Nullable Boolean shaderPack;
    private static final Map<Geometry, Mesh> MESHES = new IdentityHashMap<>();
    private static final Set<Geometry> REJECTED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static long draws;
    private static long fallbacks;
    private static long lastSweep;
    private static String lastWrapper = "";
    /**
     * Writers that put their vertices into the same batch a {@code BufferBuilder} would, only faster: Embeddium's
     * (1.20.1 Forge) hands entity renderers this class instead of the vanilla builder. Anything else that is not a
     * builder is a wrapper with a second destination (the glowing outline) and keeps the immediate path.
     */
    private static final Set<String> PLAIN_WRITERS = Set.of(
            "me.jellysquid.mods.sodium.client.render.vertex.buffer.SodiumBufferBuilder");

    private StaticMeshes() {}

    /** Client setup, once: the resource-reload release and the harness switch. */
    public static void init() {
        if (initialised) return;
        initialised = true;
        ClientResourceReloads.register("golemcore", "static_meshes", StaticMeshes::close);
        HarnessQueryRegistry.register("golem_static_meshes", (mc, params) -> query(params));
    }

    /** Whether resident drawing is in use now; while it is not, {@link #submit} is exactly the immediate submission. */
    public static boolean active() {
        if (!enabled || failed) return false;
        if (shaderPack == null) shaderPack = Platform.isModLoaded("iris") || Platform.isModLoaded("oculus");
        return !shaderPack;
    }

    private static boolean supports(Surface surface) {
        return surface.blend() == Surface.Blend.CUTOUT || surface.blend() == Surface.Blend.CUTOUT_CULL;
    }

    /**
     * Submits {@code geometry} as {@code sink.submitCustomGeometry(poseStack, surface.renderType(), (pose, buffer) ->
     * geometry.draw(pose, buffer, light, overlay, color))} would, drawn from its resident copy when that is possible.
     */
    public static void submit(SubmitSink sink, PoseStack poseStack, Surface surface, Geometry geometry, int light,
                              int overlay, int color) {
        if (!active() || !supports(surface) || REJECTED.contains(geometry)) {
            sink.submitCustomGeometry(poseStack, surface.renderType(),
                    (pose, buffer) -> geometry.draw(pose, buffer, light, overlay, color));
            return;
        }
        sink.submitCustomGeometry(poseStack, surface.renderType(), (pose, buffer) -> {
            if (!drawResident(pose, buffer, surface, geometry, light, overlay, color)) {
                fallbacks++;
                geometry.draw(pose, buffer, light, overlay, color);
            }
        });
    }

    private static boolean drawResident(PoseStack.Pose pose, VertexConsumer buffer, Surface surface, Geometry geometry,
                                        int light, int overlay, int color) {
        if (!active()) return false;
        if (!(buffer instanceof BufferBuilder) && !PLAIN_WRITERS.contains(buffer.getClass().getName())) {
            // a wrapper (the glowing outline's double consumer, a shader pack's): it must see the vertices
            lastWrapper = buffer.getClass().getName();
            return false;
        }
        Mesh mesh = MESHES.get(geometry);
        if (mesh == null) {
            if (REJECTED.contains(geometry)) return false;
            try {
                mesh = bake(geometry);
            } catch (Throwable t) {
                fail("could not upload " + geometry.name(), t);
                return false;
            }
            if (mesh == null) {
                REJECTED.add(geometry);
                GolemCore.LOG.info("Static mesh: {} does not pass its colour, light and overlay through; drawn immediately",
                        geometry.name());
                return false;
            }
            MESHES.put(geometry, mesh);
        }
        long now = System.nanoTime();
        mesh.lastUsed = now;
        try {
            Backend.draw(pose, mesh, surface, light, overlay, color);
        } catch (Throwable t) {
            fail("draw of " + geometry.name() + " failed", t);
            return false;
        }
        draws++;
        if (now - lastSweep > 1_000_000_000L) sweep(now);
        return true;
    }

    private static void fail(String what, Throwable t) {
        failed = true;
        GolemCore.LOG.warn("Static meshes switched off for this session: {}", what, t);
    }

    private static void sweep(long now) {
        lastSweep = now;
        Iterator<Mesh> meshes = MESHES.values().iterator();
        while (meshes.hasNext()) {
            Mesh mesh = meshes.next();
            if (now - mesh.lastUsed > IDLE_SECONDS * 1_000_000_000L) {
                mesh.close();
                meshes.remove();
            }
        }
    }

    /** Releases every mesh and the legacy shader. Runs on resource reload; safe to call at any time on the render thread. */
    public static void close() {
        for (Mesh mesh : MESHES.values()) mesh.close();
        MESHES.clear();
        REJECTED.clear();
        Backend.reload();
    }

    // Capture

    /**
     * Records the geometry's own vertex stream twice with an identity pose: once untinted, once with a tint. It
     * qualifies when both streams carry the same positions, texture coordinates and normals and every vertex carries
     * exactly the colour, light and overlay it was handed, in whole quads.
     */
    private static @Nullable Mesh bake(Geometry geometry) {
        Capture a = new Capture(LIGHT_A, OVERLAY_A, -1);
        geometry.draw(new PoseStack().last(), a, LIGHT_A, OVERLAY_A, -1);
        Capture b = new Capture(LIGHT_B, OVERLAY_B, COLOR_B);
        geometry.draw(new PoseStack().last(), b, LIGHT_B, OVERLAY_B, COLOR_B);
        float[] data = a.finish();
        float[] other = b.finish();
        if (data == null || other == null || !Arrays.equals(data, other)) return null;
        int vertices = data.length / Capture.FLOATS;
        if (vertices == 0 || vertices % 4 != 0) return null;
        return Backend.upload(geometry.name(), data, vertices);
    }

    /** One vertex is {@code x, y, z, u, v, nx, ny, nz}; colour, light and overlay are only checked. */
    private static final class Capture implements VertexConsumer {
        static final int FLOATS = 8;
        private final int light;
        private final int overlay;
        private final int color;
        private float[] data = new float[FLOATS * 1024];
        private int vertices;
        private int seen;
        private boolean bad;

        private static final int POSITION = 1;
        private static final int COLOUR = 2;
        private static final int UV = 4;
        private static final int OVERLAY = 8;
        private static final int LIGHT = 16;
        private static final int NORMAL = 32;
        private static final int ALL = 63;

        Capture(int light, int overlay, int color) {
            this.light = light;
            this.overlay = overlay;
            this.color = color;
        }

        private void start(float x, float y, float z) {
            if (vertices > 0 && seen != ALL) bad = true;
            if ((vertices + 1) * FLOATS > data.length) data = Arrays.copyOf(data, data.length * 2);
            int at = vertices * FLOATS;
            data[at] = x;
            data[at + 1] = y;
            data[at + 2] = z;
            vertices++;
            seen = POSITION;
        }

        private float[] slot() {
            if (vertices == 0) bad = true;
            return data;
        }

        private int at() {
            return (vertices - 1) * FLOATS;
        }

        private void putColour(int argb) {
            slot();
            if (argb != color) bad = true;
            seen |= COLOUR;
        }

        private void putUv(float u, float v) {
            if (vertices == 0) { bad = true; return; }
            data[at() + 3] = u;
            data[at() + 4] = v;
            seen |= UV;
        }

        private void putOverlay(int u, int v) {
            slot();
            if (((v & 0xFFFF) << 16 | (u & 0xFFFF)) != overlay) bad = true;
            seen |= OVERLAY;
        }

        private void putLight(int u, int v) {
            slot();
            if (((v & 0xFFFF) << 16 | (u & 0xFFFF)) != light) bad = true;
            seen |= LIGHT;
        }

        private void putNormal(float x, float y, float z) {
            if (vertices == 0) { bad = true; return; }
            data[at() + 5] = x;
            data[at() + 6] = y;
            data[at() + 7] = z;
            seen |= NORMAL;
        }

        float @Nullable [] finish() {
            if (vertices > 0 && seen != ALL) bad = true;
            return bad ? null : Arrays.copyOf(data, vertices * FLOATS);
        }

        private static int argb(int r, int g, int b, int a) {
            return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
        }

        //? if >=26.1 {
        @Override public VertexConsumer addVertex(float x, float y, float z) { start(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { putColour(argb(r, g, b, a)); return this; }
        @Override public VertexConsumer setColor(int argb) { putColour(argb); return this; }
        @Override public VertexConsumer setUv(float u, float v) { putUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { putOverlay(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { putLight(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { putNormal(x, y, z); return this; }
        @Override public VertexConsumer setLineWidth(float width) { bad = true; return this; }
        @Override public VertexConsumer addVertex(PoseStack.Pose pose, float x, float y, float z) { start(x, y, z); return this; }
        @Override public VertexConsumer addVertex(org.joml.Matrix4fc pose, float x, float y, float z) { start(x, y, z); return this; }
        @Override public VertexConsumer setNormal(PoseStack.Pose pose, float x, float y, float z) { putNormal(x, y, z); return this; }
        //?} elif >=1.21 {
        /*@Override public VertexConsumer addVertex(float x, float y, float z) { start(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { putColour(argb(r, g, b, a)); return this; }
        @Override public VertexConsumer setUv(float u, float v) { putUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { putOverlay(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { putLight(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { putNormal(x, y, z); return this; }
        @Override public VertexConsumer addVertex(PoseStack.Pose pose, float x, float y, float z) { start(x, y, z); return this; }
        @Override public VertexConsumer addVertex(org.joml.Matrix4f pose, float x, float y, float z) { start(x, y, z); return this; }
        @Override public VertexConsumer setNormal(PoseStack.Pose pose, float x, float y, float z) { putNormal(x, y, z); return this; }
        *///?} else {
        /*@Override public VertexConsumer vertex(double x, double y, double z) { start((float) x, (float) y, (float) z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { putColour(argb(r, g, b, a)); return this; }
        @Override public VertexConsumer uv(float u, float v) { putUv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { putOverlay(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { putLight(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { putNormal(x, y, z); return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) { bad = true; }
        @Override public void unsetDefaultColor() {}
        @Override public VertexConsumer vertex(org.joml.Matrix4f pose, float x, float y, float z) { start(x, y, z); return this; }
        @Override public VertexConsumer normal(org.joml.Matrix3f matrix, float x, float y, float z) { putNormal(x, y, z); return this; }
        *///?}
    }

    // Harness

    private static JsonObject query(JsonObject params) {
        if (params.has("mode")) {
            String mode = params.get("mode").getAsString();
            if (mode.equals("on")) {
                enabled = true;
                failed = false;
            } else if (mode.equals("off")) {
                enabled = false;
            }
        }
        if (params.has("reset_counters")) {
            draws = 0;
            fallbacks = 0;
        }
        JsonObject json = new JsonObject();
        json.addProperty("ok", true);
        json.addProperty("enabled", enabled);
        json.addProperty("failed", failed);
        json.addProperty("active", active());
        json.addProperty("meshes", MESHES.size());
        int vertices = 0;
        for (Mesh mesh : MESHES.values()) vertices += mesh.vertices;
        json.addProperty("vertices", vertices);
        json.addProperty("rejected", REJECTED.size());
        json.addProperty("draws", draws);
        json.addProperty("fallbacks", fallbacks);
        json.addProperty("backend", Backend.NAME);
        json.addProperty("last_wrapper", lastWrapper);
        return json;
    }

    // Backends

    /** One uploaded lump. */
    static final class Mesh {
        final int vertices;
        long lastUsed;
        //? if >=26.1 {
        final com.mojang.blaze3d.buffers.GpuBuffer buffer;

        Mesh(com.mojang.blaze3d.buffers.GpuBuffer buffer, int vertices) {
            this.buffer = buffer;
            this.vertices = vertices;
        }

        void close() {
            buffer.close();
        }
        //?} else {
        /*final com.mojang.blaze3d.vertex.VertexBuffer buffer;

        Mesh(com.mojang.blaze3d.vertex.VertexBuffer buffer, int vertices) {
            this.buffer = buffer;
            this.vertices = vertices;
        }

        void close() {
            buffer.close();
        }
        *///?}
    }

    /** The era-specific upload and draw. */
    private static final class Backend {

        private Backend() {}

        /**
         * Whether the pose would normalise a transformed normal ({@code PoseStack.Pose.transformNormal}). The flag
         * itself is private; the public method answers it exactly: normalising the zero vector gives NaN.
         */
        private static boolean normalises(PoseStack.Pose pose) {
            //? if >=1.21 {
            return Float.isNaN(pose.transformNormal(0F, 0F, 0F, new org.joml.Vector3f()).x);
            //?} else {
            /*return false; // 1.20.1 VertexConsumer.normal(Matrix3f, ...) never normalises
            *///?}
        }

        //? if >=26.1 {
        static final String NAME = "gpu-buffer";
        private static final int BLOCK_BYTES = 176;
        private static final int RING_SLOTS = 256;
        private static com.mojang.blaze3d.vertex.@Nullable VertexFormat format;
        private static com.mojang.blaze3d.pipeline.@Nullable RenderPipeline pipeline;
        private static com.mojang.blaze3d.buffers.@Nullable GpuBuffer ring;
        private static int slotBytes;
        private static int slot;
        private static final java.nio.ByteBuffer BLOCK = org.lwjgl.system.MemoryUtil.memAlloc(BLOCK_BYTES);
        private static final org.joml.Matrix4f NORMAL = new org.joml.Matrix4f();
        private static final org.joml.Vector4f WHITE = new org.joml.Vector4f(1F, 1F, 1F, 1F);
        private static final org.joml.Vector3f NO_OFFSET = new org.joml.Vector3f();

        private static com.mojang.blaze3d.vertex.VertexFormat format() {
            if (format == null) {
                // Model-space normals stay floats: vanilla's NORMAL element is the byte the immediate path writes
                // after transforming, and quantising before the transform would round a different value.
                // the highest free id (findNextId is NeoForge-only), as far from vanilla's and other mods' as it gets
                int id = com.mojang.blaze3d.vertex.VertexFormatElement.MAX_COUNT - 1;
                while (id >= 0 && com.mojang.blaze3d.vertex.VertexFormatElement.byId(id) != null) id--;
                if (id < 0) throw new IllegalStateException("No free vertex format element id");
                com.mojang.blaze3d.vertex.VertexFormatElement normal = com.mojang.blaze3d.vertex.VertexFormatElement.register(
                        id, 0,
                        com.mojang.blaze3d.vertex.VertexFormatElement.Type.FLOAT, false, 3);
                format = com.mojang.blaze3d.vertex.VertexFormat.builder()
                        .add("Position", com.mojang.blaze3d.vertex.VertexFormatElement.POSITION)
                        .add("UV0", com.mojang.blaze3d.vertex.VertexFormatElement.UV0)
                        .add("Normal", normal)
                        .build();
            }
            return format;
        }

        /**
         * {@code RenderPipelines.ENTITY_CUTOUT_CULL} with only the vertex shader, its input format and the one extra
         * uniform block changed, checked against the live vanilla pipeline so a patched one is never imitated.
         */
        private static com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
            if (pipeline == null) {
                com.mojang.blaze3d.pipeline.RenderPipeline vanilla = net.minecraft.client.renderer.RenderPipelines.ENTITY_CUTOUT_CULL;
                com.mojang.blaze3d.pipeline.RenderPipeline built = com.mojang.blaze3d.pipeline.RenderPipeline
                        .builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
                        .withLocation(dev.golem.core.Ids.of("golemcore", "pipeline/static_mesh_entity_cutout_cull"))
                        .withVertexShader(dev.golem.core.Ids.of("golemcore", "core/static_mesh"))
                        .withFragmentShader("core/entity")
                        .withSampler("Sampler0")
                        .withSampler("Sampler2")
                        .withSampler("Sampler1")
                        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                        .withUniform("StaticMesh", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                        .withVertexFormat(format(), com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
                        .withDepthStencilState(com.mojang.blaze3d.pipeline.DepthStencilState.DEFAULT)
                        .build();
                if (!built.getFragmentShader().equals(vanilla.getFragmentShader())
                        || !built.getShaderDefines().equals(vanilla.getShaderDefines())
                        || built.isCull() != vanilla.isCull()
                        || !built.getColorTargetState().equals(vanilla.getColorTargetState())
                        || !java.util.Objects.equals(built.getDepthStencilState(), vanilla.getDepthStencilState())
                        || built.getPolygonMode() != vanilla.getPolygonMode()
                        || !new java.util.HashSet<>(built.getSamplers()).equals(new java.util.HashSet<>(vanilla.getSamplers()))
                        || vanilla.getVertexFormatMode() != com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS
                        || !vanilla.getVertexShader().equals(net.minecraft.resources.Identifier.withDefaultNamespace("core/entity"))) {
                    throw new IllegalStateException("entity_cutout_cull is not the pipeline this mirror was written for: " + vanilla);
                }
                pipeline = built;
            }
            return pipeline;
        }

        static Mesh upload(String name, float[] data, int vertices) {
            java.nio.ByteBuffer bytes = org.lwjgl.system.MemoryUtil.memAlloc(data.length * 4);
            try {
                bytes.asFloatBuffer().put(data);
                com.mojang.blaze3d.buffers.GpuBuffer buffer = com.mojang.blaze3d.systems.RenderSystem.getDevice()
                        .createBuffer(() -> "Golem static mesh " + name, com.mojang.blaze3d.buffers.GpuBuffer.USAGE_VERTEX, bytes);
                return new Mesh(buffer, vertices);
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(bytes);
            }
        }

        private static com.mojang.blaze3d.buffers.GpuBufferSlice block(PoseStack.Pose pose, int light, int overlay, int color) {
            com.mojang.blaze3d.systems.GpuDevice device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
            if (ring == null) {
                int alignment = Math.max(1, device.getUniformOffsetAlignment());
                slotBytes = (BLOCK_BYTES + alignment - 1) / alignment * alignment;
                ring = device.createBuffer(() -> "Golem static mesh parts",
                        com.mojang.blaze3d.buffers.GpuBuffer.USAGE_UNIFORM | com.mojang.blaze3d.buffers.GpuBuffer.USAGE_COPY_DST,
                        (long) slotBytes * RING_SLOTS);
            }
            NORMAL.set(pose.normal());
            BLOCK.clear();
            com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(BLOCK)
                    .putMat4f(pose.pose())
                    .putMat4f(NORMAL)
                    .putVec4(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F,
                            ((color >>> 24) & 0xFF) / 255F)
                    .putIVec4((short) (light & 0xFFFF), (short) ((light >> 16) & 0xFFFF),
                            (short) (overlay & 0xFFFF), (short) ((overlay >> 16) & 0xFFFF))
                    .putIVec4(normalises(pose) ? 1 : 0, 0, 0, 0);
            BLOCK.rewind();
            com.mojang.blaze3d.buffers.GpuBufferSlice slice = ring.slice((long) slot * slotBytes, BLOCK_BYTES);
            slot = (slot + 1) % RING_SLOTS;
            device.createCommandEncoder().writeToBuffer(slice, BLOCK);
            return slice;
        }

        /** {@code RenderType.draw} for {@code entity_cutout_cull}, with the resident mesh as the vertex buffer. */
        static void draw(PoseStack.Pose pose, Mesh mesh, Surface surface, int light, int overlay, int color) {
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline = pipeline();
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            com.mojang.blaze3d.buffers.GpuBufferSlice part = block(pose, light, overlay, color);
            com.mojang.blaze3d.buffers.GpuBufferSlice transforms = com.mojang.blaze3d.systems.RenderSystem.getDynamicUniforms()
                    .writeTransform(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix(), WHITE, NO_OFFSET,
                            net.minecraft.client.renderer.rendertype.TextureTransform.DEFAULT_TEXTURING.getMatrix());
            int indexCount = mesh.vertices / 4 * 6;
            com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer indices =
                    com.mojang.blaze3d.systems.RenderSystem.getSequentialBuffer(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS);
            com.mojang.blaze3d.buffers.GpuBuffer indexBuffer = indices.getBuffer(indexCount);
            com.mojang.blaze3d.pipeline.RenderTarget target =
                    net.minecraft.client.renderer.rendertype.OutputTarget.MAIN_TARGET.getRenderTarget();
            com.mojang.blaze3d.textures.GpuTextureView colorTexture = com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride != null
                    ? com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride : target.getColorTextureView();
            com.mojang.blaze3d.textures.GpuTextureView depthTexture = target.useDepth
                    ? (com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride != null
                    ? com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
                    : null;
            net.minecraft.client.renderer.texture.AbstractTexture texture =
                    minecraft.getTextureManager().getTexture(dev.golem.core.Ids.parse(surface.texture()));
            com.mojang.blaze3d.textures.GpuSampler linear = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                    .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
            try (com.mojang.blaze3d.systems.RenderPass pass = com.mojang.blaze3d.systems.RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(() -> "Golem static mesh", colorTexture, java.util.OptionalInt.empty(), depthTexture,
                            java.util.OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                com.mojang.blaze3d.systems.ScissorState scissor = com.mojang.blaze3d.systems.RenderSystem.getScissorStateForRenderTypeDraws();
                if (scissor.enabled()) pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setUniform("StaticMesh", part);
                pass.setVertexBuffer(0, mesh.buffer);
                pass.bindTexture("Sampler1", minecraft.gameRenderer.overlayTexture().getTextureView(), linear);
                pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), linear);
                pass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.drawIndexed(0, 0, indexCount, 1);
            }
        }

        static void reload() {
            // 26.1 recompiles pipelines itself after a shader reload; the ring and the format survive.
        }
        //?} else {
        /*static final String NAME = "vertex-buffer";
        private static com.mojang.blaze3d.vertex.@Nullable VertexFormat format;
        private static net.minecraft.client.renderer.@Nullable ShaderInstance shader;

        private static com.mojang.blaze3d.vertex.VertexFormat format() {
            if (format == null) {
                //? if >=1.21 {
                int id = 31;
                while (id >= 0 && com.mojang.blaze3d.vertex.VertexFormatElement.byId(id) != null) id--;
                if (id < 0) throw new IllegalStateException("No free vertex format element id");
                com.mojang.blaze3d.vertex.VertexFormatElement normal = com.mojang.blaze3d.vertex.VertexFormatElement.register(
                        id, 0, com.mojang.blaze3d.vertex.VertexFormatElement.Type.FLOAT,
                        com.mojang.blaze3d.vertex.VertexFormatElement.Usage.GENERIC, 3);
                format = com.mojang.blaze3d.vertex.VertexFormat.builder()
                        .add("Position", com.mojang.blaze3d.vertex.VertexFormatElement.POSITION)
                        .add("UV0", com.mojang.blaze3d.vertex.VertexFormatElement.UV0)
                        .add("Normal", normal)
                        .build();
                //?} else {
                /^com.mojang.blaze3d.vertex.VertexFormatElement normal = new com.mojang.blaze3d.vertex.VertexFormatElement(
                        0, com.mojang.blaze3d.vertex.VertexFormatElement.Type.FLOAT,
                        com.mojang.blaze3d.vertex.VertexFormatElement.Usage.GENERIC, 3);
                format = new com.mojang.blaze3d.vertex.VertexFormat(com.google.common.collect.ImmutableMap.of(
                        "Position", com.mojang.blaze3d.vertex.DefaultVertexFormat.ELEMENT_POSITION,
                        "UV0", com.mojang.blaze3d.vertex.DefaultVertexFormat.ELEMENT_UV0,
                        "Normal", normal));
                ^///?}
            }
            return format;
        }

        private static net.minecraft.client.renderer.ShaderInstance shader() throws java.io.IOException {
            if (shader == null) {
                //? if >=1.21 {
                shader = ShaderLoader.load(net.minecraft.client.Minecraft.getInstance().getResourceManager(),
                        "golemcore:static_mesh_legacy", format());
                //?} else {
                /^shader = ShaderLoader.load(net.minecraft.client.Minecraft.getInstance().getResourceManager(),
                        "golemcore:static_mesh_legacy120", format());
                ^///?}
            }
            return shader;
        }

        static Mesh upload(String name, float[] data, int vertices) {
            com.mojang.blaze3d.vertex.VertexBuffer buffer =
                    new com.mojang.blaze3d.vertex.VertexBuffer(com.mojang.blaze3d.vertex.VertexBuffer.Usage.STATIC);
            //? if >=1.21 {
            try (com.mojang.blaze3d.vertex.ByteBufferBuilder bytes = new com.mojang.blaze3d.vertex.ByteBufferBuilder(data.length * 4)) {
                long pointer = bytes.reserve(data.length * 4);
                for (int i = 0; i < data.length; i++) org.lwjgl.system.MemoryUtil.memPutFloat(pointer + 4L * i, data[i]);
                try (com.mojang.blaze3d.vertex.MeshData mesh = new com.mojang.blaze3d.vertex.MeshData(bytes.build(),
                        new com.mojang.blaze3d.vertex.MeshData.DrawState(format(), vertices, vertices / 4 * 6,
                                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                                com.mojang.blaze3d.vertex.VertexFormat.IndexType.least(vertices)))) {
                    buffer.bind();
                    buffer.upload(mesh);
                    com.mojang.blaze3d.vertex.VertexBuffer.unbind();
                }
            }
            //?} else {
            /^com.mojang.blaze3d.vertex.BufferBuilder builder = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            if (builder.building()) throw new IllegalStateException("the tesselator is in use");
            builder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, format());
            for (int at = 0; at < data.length; at += Capture.FLOATS) {
                builder.vertex(data[at], data[at + 1], data[at + 2]);
                builder.uv(data[at + 3], data[at + 4]);
                builder.putFloat(0, data[at + 5]);
                builder.putFloat(4, data[at + 6]);
                builder.putFloat(8, data[at + 7]);
                builder.nextElement();
                builder.endVertex();
            }
            buffer.bind();
            buffer.upload(builder.end());
            com.mojang.blaze3d.vertex.VertexBuffer.unbind();
            ^///?}
            return new Mesh(buffer, vertices);
        }

        private static void set(net.minecraft.client.renderer.ShaderInstance shader, String name, int x, int y) {
            com.mojang.blaze3d.shaders.Uniform uniform = shader.getUniform(name);
            if (uniform != null) uniform.set(x, y);
        }

        // RenderType.end and draw for the entity cutout type: its state set up and cleared around the draw exactly
        // as for the batch, the resident buffer drawn with the uniforms the batch would have had.
        static void draw(PoseStack.Pose pose, Mesh mesh, Surface surface, int light, int overlay, int color) throws java.io.IOException {
            net.minecraft.client.renderer.ShaderInstance program = shader();
            net.minecraft.client.renderer.RenderType type = surface.renderType();
            type.setupRenderState();
            try {
                com.mojang.blaze3d.shaders.Uniform uniform = program.getUniform("MeshPose");
                if (uniform != null) uniform.set(pose.pose());
                uniform = program.getUniform("MeshNormal");
                if (uniform != null) uniform.set(pose.normal());
                uniform = program.getUniform("MeshColor");
                if (uniform != null) uniform.set(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F,
                        (color & 0xFF) / 255F, ((color >>> 24) & 0xFF) / 255F);
                set(program, "MeshLight", (short) (light & 0xFFFF), (short) ((light >> 16) & 0xFFFF));
                set(program, "MeshOverlay", (short) (overlay & 0xFFFF), (short) ((overlay >> 16) & 0xFFFF));
                uniform = program.getUniform("MeshNormalize");
                if (uniform != null) uniform.set(normalises(pose) ? 1 : 0);
                mesh.buffer.bind();
                mesh.buffer.drawWithShader(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix(),
                        com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(), program);
                com.mojang.blaze3d.vertex.VertexBuffer.unbind();
            } finally {
                type.clearRenderState();
            }
        }

        static void reload() {
            if (shader != null) shader.close();
            shader = null;
        }
        *///?}
    }
}
