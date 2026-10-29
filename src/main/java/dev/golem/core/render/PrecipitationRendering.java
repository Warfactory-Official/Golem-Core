package dev.golem.core.render;

import dev.golem.core.client.Precipitation;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Client half of {@link Precipitation}: installs each node's hook and draws a registered level's weather quads in
 * vanilla's weather pass with 1.7's weather state.
 *
 * <ul>
 *   <li>26.1: a copy of vanilla's {@code WEATHER_DEPTH_WRITE} / {@code WEATHER_NO_DEPTH_WRITE} pipelines (chosen as vanilla
 *       chooses them) whose fragment shader is {@code golemcore:core/weather_tint}, drawn into the weather target as
 *       {@code WeatherEffectRenderer.render} draws, one indexed draw per batch.</li>
 *   <li>1.21.1 / 1.20.1: vanilla's {@code particle} vertex shader with the fragment {@code golemcore:weather_tint_legacy}
 *       (program {@code golemcore:weather_tint}), set up as vanilla's {@code renderSnowAndRain} sets up its own draw.</li>
 * </ul>
 */
public final class PrecipitationRendering {

    private PrecipitationRendering() {}

    private static boolean hooked;
    private static final Set<ResourceKey<Level>> INSTALLED = new HashSet<>();

    /** Installs the node's hook (once) and, where the hook is per dimension, for {@code dimension}. */
    public static synchronized void install(ResourceKey<Level> dimension) {
        if (!INSTALLED.add(dimension)) return;
        //? if fabric && <26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry.registerWeatherRenderer(dimension, context -> {
            var camera = context.camera().getPosition();
            renderLegacy(context.lightmapTextureManager(), dev.golem.core.client.FrameTime.partialTick(), camera.x, camera.y, camera.z);
        });
        *///?}
        if (hooked) return;
        hooked = true;
        //? if neoforge && >=26.1 {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent event) ->
                        event.registerWeatherEffectRenderer(dev.golem.core.Ids.of(NAMESPACE, PATH),
                                new net.neoforged.neoforge.client.CustomWeatherEffectRenderer() {
                                    @Override
                                    public boolean renderSnowAndRain(net.minecraft.client.renderer.state.level.LevelRenderState state,
                                                                     net.minecraft.client.renderer.state.level.WeatherRenderState weather,
                                                                     net.minecraft.client.renderer.MultiBufferSource buffers,
                                                                     Vec3 camera) {
                                        return render(camera);
                                    }
                                }));
        //?}
    }

    /** NeoForge 26.1's custom weather renderer id for Golem's ({@code golemcore:weather}). */
    public static final String NAMESPACE = "golemcore";
    public static final String PATH = "weather";

    //? if >=26.1 {
    private static final java.util.Map<Boolean, com.mojang.blaze3d.pipeline.RenderPipeline> PIPELINES = new java.util.HashMap<>();

    private static com.mojang.blaze3d.pipeline.RenderPipeline pipeline(boolean depthWrite) {
        return PIPELINES.computeIfAbsent(depthWrite, write -> {
            com.mojang.blaze3d.pipeline.RenderPipeline.Builder builder = com.mojang.blaze3d.pipeline.RenderPipeline
                    .builder(net.minecraft.client.renderer.RenderPipelines.PARTICLE_SNIPPET)
                    // vanilla's WEATHER_SNIPPET (not visible on every loader): translucent blend, both faces
                    .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(
                            com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withLocation(dev.golem.core.Ids.of("golemcore", write ? "pipeline/weather_tint_depth_write"
                            : "pipeline/weather_tint_no_depth_write"))
                    .withFragmentShader(dev.golem.core.Ids.of("golemcore", "core/weather_tint"));
            if (!write) {
                builder.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
                        com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false));
            }
            return WorldPipelines.add(builder.build());
        });
    }

    /**
     * 26.1: draws the client level's weather in place of vanilla's (inside vanilla's weather pass). False (vanilla draws)
     * when the level is not registered with {@link Precipitation}.
     */
    public static boolean render(Vec3 camera) {
        ClientLevel level = Minecraft.getInstance().level;
        if (!Precipitation.has(level)) return false;
        Precipitation.Frame frame = Precipitation.collect(level, dev.golem.core.client.FrameTime.partialTick(), camera);
        if (frame == null || frame.batches().isEmpty()) return true;
        int quads = 0;
        for (Precipitation.Batch batch : frame.batches()) quads += batch.vertices().length / 40;
        com.mojang.blaze3d.pipeline.RenderPipeline pipeline = pipeline(Minecraft.useShaderTransparency());
        com.mojang.blaze3d.vertex.VertexFormat format = com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE;
        com.mojang.blaze3d.buffers.GpuBuffer vertexBuffer;
        com.mojang.blaze3d.buffers.GpuBuffer indexBuffer;
        com.mojang.blaze3d.vertex.VertexFormat.IndexType indexType;
        try (com.mojang.blaze3d.vertex.ByteBufferBuilder bytes =
                     com.mojang.blaze3d.vertex.ByteBufferBuilder.exactlySized(quads * format.getVertexSize() * 4)) {
            com.mojang.blaze3d.vertex.BufferBuilder builder = new com.mojang.blaze3d.vertex.BufferBuilder(bytes,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, format);
            for (Precipitation.Batch batch : frame.batches()) {
                float[] v = batch.vertices();
                for (int i = 0; i < v.length / 40 * 4; i++) {
                    int o = i * 10;
                    builder.addVertex(v[o], v[o + 1], v[o + 2])
                            .setUv(v[o + 3], v[o + 4]).setColor(v[o + 5], v[o + 6], v[o + 7], v[o + 8]).setLight((int) v[o + 9]);
                }
            }
            try (com.mojang.blaze3d.vertex.MeshData mesh = builder.buildOrThrow()) {
                vertexBuffer = pipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
                com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer indices =
                        com.mojang.blaze3d.systems.RenderSystem.getSequentialBuffer(mesh.drawState().mode());
                indexBuffer = indices.getBuffer(mesh.drawState().indexCount());
                indexType = indices.type();
            }
        }
        com.mojang.blaze3d.buffers.GpuBufferSlice transforms = com.mojang.blaze3d.systems.RenderSystem.getDynamicUniforms()
                .writeTransform(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix(), new org.joml.Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                        new org.joml.Vector3f(), new org.joml.Matrix4f());
        com.mojang.blaze3d.pipeline.RenderTarget target = net.minecraft.client.renderer.rendertype.OutputTarget.WEATHER_TARGET.getRenderTarget();
        net.minecraft.client.renderer.texture.TextureManager textures = Minecraft.getInstance().getTextureManager();
        // Resolve (and on first use load) every batch texture before the pass opens: a first load uploads through the
        // command encoder, which blaze3d refuses while a render pass is open.
        java.util.List<com.mojang.blaze3d.textures.GpuTextureView> views = new java.util.ArrayList<>();
        for (Precipitation.Batch batch : frame.batches()) views.add(textures.getTexture(dev.golem.core.Ids.parse(batch.texture())).getTextureView());
        try (com.mojang.blaze3d.systems.RenderPass pass = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Golem weather", target.getColorTextureView(), java.util.OptionalInt.empty(),
                        target.getDepthTextureView(), java.util.OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(),
                    com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR));
            pass.setIndexBuffer(indexBuffer, indexType);
            pass.setVertexBuffer(0, vertexBuffer);
            int first = 0;
            int index = 0;
            for (Precipitation.Batch batch : frame.batches()) {
                int count = batch.vertices().length / 40;
                // 1.7's texture-manager state: GL_REPEAT, GL_NEAREST
                pass.bindTexture("Sampler0", views.get(index++),
                        com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getRepeat(com.mojang.blaze3d.textures.FilterMode.NEAREST));
                pass.drawIndexed(0, first * 6, count * 6, 1);
                first += count;
            }
        }
        Precipitation.countDrawn(quads);
        return true;
    }
    //?} else {
    /*private static net.minecraft.client.renderer.ShaderInstance shader;

    private static net.minecraft.client.renderer.ShaderInstance shader() {
        if (shader == null) {
            try {
                shader = ShaderLoader.load(Minecraft.getInstance().getResourceManager(), "golemcore:weather_tint",
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE, PrecipitationRendering::blend);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not load shader golemcore:weather_tint", e);
            }
        }
        return shader;
    }

    /^* 1.7's {@code OpenGlHelper.glBlendFunc(770, 771, 1, 0)}, set again after every {@code apply()} (the 1.20.1 trap). ^/
    private static void blend() {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
    }

    /^*
     * 1.21.1 / 1.20.1: draws the client level's weather in place of vanilla's {@code renderSnowAndRain} (called where it
     * is, in its target and depth-mask state). False (vanilla draws) when the level is not registered.
     ^/
    public static boolean renderLegacy(net.minecraft.client.renderer.LightTexture lightTexture, float partialTick, double camX,
                                       double camY, double camZ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (!Precipitation.has(level)) return false;
        Precipitation.Frame frame = Precipitation.collect(level, partialTick, new Vec3(camX, camY, camZ));
        if (frame == null || frame.batches().isEmpty()) return true;
        lightTexture.turnOnLightLayer();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(Minecraft.useShaderTransparency());
        net.minecraft.client.renderer.ShaderInstance program = shader();
        com.mojang.blaze3d.systems.RenderSystem.setShader(() -> program);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        blend();
        int quads = 0;
        for (Precipitation.Batch batch : frame.batches()) {
            float[] v = batch.vertices();
            int count = v.length / 40;
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, dev.golem.core.Ids.parse(batch.texture()));
            //? if >=1.21 {
            com.mojang.blaze3d.vertex.BufferBuilder builder = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE);
            for (int i = 0; i < count * 4; i++) {
                int o = i * 10;
                builder.addVertex(v[o], v[o + 1], v[o + 2])
                        .setUv(v[o + 3], v[o + 4]).setColor(v[o + 5], v[o + 6], v[o + 7], v[o + 8]).setLight((int) v[o + 9]);
            }
            com.mojang.blaze3d.vertex.MeshData mesh = builder.build();
            if (mesh != null) com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
            //?} else {
            /^com.mojang.blaze3d.vertex.BufferBuilder builder = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            builder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE);
            for (int i = 0; i < count * 4; i++) {
                int o = i * 10;
                builder.vertex(v[o], v[o + 1], v[o + 2]).uv(v[o + 3], v[o + 4])
                        .color(v[o + 5], v[o + 6], v[o + 7], v[o + 8]).uv2((int) v[o + 9]).endVertex();
            }
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(builder.end());
            ^///?}
            quads += count;
        }
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        lightTexture.turnOffLightLayer();
        Precipitation.countDrawn(quads);
        return true;
    }
    *///?}
}
