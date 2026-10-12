package dev.golem.core.render;

import dev.golem.core.client.Clouds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Client half of {@link Clouds}: installs each node's hook and draws a registered level's layers through that node's own
 * vanilla cloud renderer, so a layer looks like vanilla's clouds on the same node, only at the layer's height, colour,
 * alpha and texture position.
 *
 * <ul>
 *   <li>26.1: one vanilla {@code CloudRenderer} per layer index (its mesh cache, uniform ring and texel buffer are per
 *       instance, so layers drawn in one frame never overwrite each other's buffers), loaded from the same
 *       {@code clouds.png} and reloaded with the resources. Each is called as vanilla calls its own, with
 *       {@code gameTime} and the partial tick 0 so that vanilla's drift is not added; {@code endFrame} rotates its uniform
 *       ring after the draw, as vanilla does once a frame for its own.</li>
 *   <li>1.21.1 / 1.20.1: a port of vanilla's {@code LevelRenderer.renderClouds} / {@code buildClouds} with the layer's
 *       values in place of the dimension's cloud height, {@code getCloudColor}, the 0.8 alpha and the tick drift. The
 *       vertex buffer of each layer index is rebuilt only when vanilla would rebuild its own (cell, height step, colour,
 *       alpha or cloud setting changed). Drawn with vanilla's own cloud shader state, fog and clouds target.</li>
 * </ul>
 */
public final class CloudRendering {

    private CloudRendering() {}

    private static boolean hooked;
    private static final Set<ResourceKey<Level>> INSTALLED = new HashSet<>();

    /** Installs the node's hook (once) and, where the hook is per dimension, for {@code dimension}. */
    public static synchronized void install(ResourceKey<Level> dimension) {
        if (!INSTALLED.add(dimension)) return;
        //? if fabric && <26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry.registerCloudRenderer(dimension, context -> {
            var camera = context.camera().getPosition();
            //? if >=1.21 {
            com.mojang.blaze3d.vertex.PoseStack pose = new com.mojang.blaze3d.vertex.PoseStack();
            renderLegacy(pose, context.positionMatrix(), context.projectionMatrix(), dev.golem.core.client.FrameTime.partialTick(),
                    camera.x, camera.y, camera.z);
            //?} else {
            /^renderLegacy(context.matrixStack(), null, context.projectionMatrix(), dev.golem.core.client.FrameTime.partialTick(),
                    camera.x, camera.y, camera.z);
            ^///?}
        });
        *///?}
        if (hooked) return;
        hooked = true;
        dev.golem.core.client.ClientResourceReloads.register(Clouds.NAMESPACE, Clouds.PATH, CloudRendering::reload);
        //? if neoforge && >=26.1 {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent event) ->
                        event.registerCloudRenderer(dev.golem.core.Ids.of(Clouds.NAMESPACE, Clouds.PATH),
                                new net.neoforged.neoforge.client.CustomCloudsRenderer() {
                                    @Override
                                    public boolean renderClouds(net.minecraft.client.renderer.state.level.LevelRenderState state,
                                                                net.minecraft.world.phys.Vec3 camera,
                                                                net.minecraft.client.CloudStatus status, int color,
                                                                float height, int range, org.joml.Matrix4fc modelView) {
                                        return render(status, range, camera);
                                    }
                                }));
        //?}
    }

    /** Whether {@code renderer} is one of Golem's own layer renderers (26.1; the vanilla hook must not take them over). */
    public static boolean isLayerRenderer(Object renderer) {
        //? if >=26.1 {
        return renderer instanceof LayerRenderer;
        //?} else {
        /*return false;
        *///?}
    }

    private static volatile boolean reloadPending;
    private static long layersDrawn;

    /** How many cloud layers Golem has drawn since start (a positive signal for tests that the node's hook runs). */
    public static long layersDrawn() {
        return layersDrawn;
    }

    private static void reload() {
        reloadPending = true;
    }

    //? if >=26.1 {

    /** A vanilla cloud renderer that loads its own copy of the cloud texture (26.1). */
    private static final class LayerRenderer extends net.minecraft.client.renderer.CloudRenderer {
        void load() {
            net.minecraft.server.packs.resources.ResourceManager resources = Minecraft.getInstance().getResourceManager();
            net.minecraft.util.profiling.ProfilerFiller profiler = net.minecraft.util.profiling.InactiveProfiler.INSTANCE;
            this.apply(this.prepare(resources, profiler), resources, profiler);
        }
    }

    private static final List<LayerRenderer> RENDERERS = new ArrayList<>();

    /**
     * 26.1: draws the client level's layers in place of vanilla's clouds. False (vanilla draws) when the level is not
     * registered with {@link Clouds}.
     */
    public static boolean render(net.minecraft.client.CloudStatus status, int range, net.minecraft.world.phys.Vec3 camera) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (!Clouds.has(level)) return false;
        List<Clouds.Layer> layers = Clouds.layers(level, dev.golem.core.client.FrameTime.partialTick());
        if (reloadPending) {
            reloadPending = false;
            for (LayerRenderer renderer : RENDERERS) renderer.load();
        }
        while (RENDERERS.size() < layers.size()) {
            LayerRenderer renderer = new LayerRenderer();
            renderer.load();
            RENDERERS.add(renderer);
        }
        for (int i = 0; i < layers.size(); i++) {
            Clouds.Layer layer = layers.get(i);
            LayerRenderer renderer = RENDERERS.get(i);
            int argb = net.minecraft.util.ARGB.colorFromFloat(layer.alpha(), layer.red(), layer.green(), layer.blue());
            net.minecraft.world.phys.Vec3 shifted = new net.minecraft.world.phys.Vec3(camera.x + layer.shiftX(), camera.y,
                    camera.z + layer.shiftZ());
            // bottom: vanilla's cloud_height attribute carries the 0.33 lift (192.33); gameTime and partial tick 0: no drift
            renderer.render(argb, status, layer.height() + 0.33F, range, shifted, 0L, 0.0F);
            renderer.endFrame();
            layersDrawn++;
        }
        return true;
    }
    //?} else {
    /*private static final List<LegacyLayer> LEGACY = new ArrayList<>();

    /^* One layer's vertex buffer and the values it was built for (vanilla's prevCloudX/Y/Z, colour and setting). ^/
    private static final class LegacyLayer {
        com.mojang.blaze3d.vertex.VertexBuffer buffer;
        int cellX = Integer.MIN_VALUE, cellY = Integer.MIN_VALUE, cellZ = Integer.MIN_VALUE;
        float red = -1, green = -1, blue = -1, alpha = -1;
        net.minecraft.client.CloudStatus status;
    }

    /^* One vertex of {@code POSITION_TEX_COLOR_NORMAL}. ^/
    private interface Sink {
        void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a, float nx, float ny, float nz);
    }

    /^*
     * Before 26.1: draws the client level's layers where vanilla's {@code renderClouds} would draw its clouds, with the
     * same arguments ({@code modelView} is 1.21.1's frustum matrix, null on 1.20.1 where {@code pose} carries it).
     ^/
    public static void renderLegacy(com.mojang.blaze3d.vertex.PoseStack pose, org.joml.Matrix4f modelView,
                                    org.joml.Matrix4f projection, float partialTick, double camX, double camY, double camZ) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        if (!Clouds.has(level)) return;
        List<Clouds.Layer> layers = Clouds.layers(level, partialTick);
        if (layers.isEmpty()) return;
        if (reloadPending) {
            reloadPending = false;
            for (LegacyLayer cache : LEGACY) cache.status = null; // rebuild
        }
        net.minecraft.client.CloudStatus status = mc.options.getCloudsType();
        while (LEGACY.size() < layers.size()) LEGACY.add(new LegacyLayer());
        //? if <1.21 {
        /^com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        ^///?}
        for (int i = 0; i < layers.size(); i++) {
            drawLegacyLayer(LEGACY.get(i), layers.get(i), status, pose, modelView, projection, camX, camY, camZ);
        }
        //? if <1.21 {
        /^com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        ^///?}
    }

    /^* Vanilla's {@code renderClouds} body for one layer. ^/
    private static void drawLegacyLayer(LegacyLayer cache, Clouds.Layer layer, net.minecraft.client.CloudStatus status,
                                        com.mojang.blaze3d.vertex.PoseStack pose, org.joml.Matrix4f modelView,
                                        org.joml.Matrix4f projection, double camX, double camY, double camZ) {
        double d2 = (camX + layer.shiftX()) / 12.0;
        double d3 = (double) (layer.height() - (float) camY + 0.33F);
        double d4 = (camZ + layer.shiftZ()) / 12.0 + 0.33F;
        d2 -= (double) (net.minecraft.util.Mth.floor(d2 / 2048.0) * 2048);
        d4 -= (double) (net.minecraft.util.Mth.floor(d4 / 2048.0) * 2048);
        float f3 = (float) (d2 - (double) net.minecraft.util.Mth.floor(d2));
        float f4 = (float) (d3 / 4.0 - (double) net.minecraft.util.Mth.floor(d3 / 4.0)) * 4.0F;
        float f5 = (float) (d4 - (double) net.minecraft.util.Mth.floor(d4));
        int i = (int) Math.floor(d2);
        int j = (int) Math.floor(d3 / 4.0);
        int k = (int) Math.floor(d4);
        if (cache.buffer == null || i != cache.cellX || j != cache.cellY || k != cache.cellZ || status != cache.status
                || layer.red() != cache.red || layer.green() != cache.green || layer.blue() != cache.blue
                || layer.alpha() != cache.alpha) {
            cache.cellX = i;
            cache.cellY = j;
            cache.cellZ = k;
            cache.status = status;
            cache.red = layer.red();
            cache.green = layer.green();
            cache.blue = layer.blue();
            cache.alpha = layer.alpha();
            if (cache.buffer != null) cache.buffer.close();
            cache.buffer = new com.mojang.blaze3d.vertex.VertexBuffer(com.mojang.blaze3d.vertex.VertexBuffer.Usage.STATIC);
            //? if >=1.21 {
            com.mojang.blaze3d.vertex.BufferBuilder builder = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            build((x, y, z, u, v, r, g, b, a, nx, ny, nz) -> builder.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a)
                    .setNormal(nx, ny, nz), status, d2, d3, d4, layer);
            cache.buffer.bind();
            cache.buffer.upload(builder.buildOrThrow());
            //?} else {
            /^com.mojang.blaze3d.vertex.BufferBuilder builder = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorNormalShader);
            builder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            build((x, y, z, u, v, r, g, b, a, nx, ny, nz) -> builder.vertex(x, y, z).uv(u, v).color(r, g, b, a)
                    .normal(nx, ny, nz).endVertex(), status, d2, d3, d4, layer);
            com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer rendered = builder.end();
            cache.buffer.bind();
            cache.buffer.upload(rendered);
            ^///?}
            com.mojang.blaze3d.vertex.VertexBuffer.unbind();
        }

        //? if <1.21 {
        /^com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorNormalShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, CLOUDS);
        ^///?}
        net.minecraft.client.renderer.FogRenderer.levelFogColor();
        pose.pushPose();
        //? if >=1.21 {
        pose.mulPose(modelView);
        //?}
        pose.scale(12.0F, 1.0F, 12.0F);
        pose.translate(-f3, f4, -f5);
        cache.buffer.bind();
        int first = cache.status == net.minecraft.client.CloudStatus.FANCY ? 0 : 1;
        for (int pass = first; pass < 2; pass++) {
            //? if >=1.21 {
            net.minecraft.client.renderer.RenderType type = pass == 0
                    ? net.minecraft.client.renderer.RenderType.cloudsDepthOnly() : net.minecraft.client.renderer.RenderType.clouds();
            type.setupRenderState();
            cache.buffer.drawWithShader(pose.last().pose(), projection, com.mojang.blaze3d.systems.RenderSystem.getShader());
            type.clearRenderState();
            //?} else {
            /^if (pass == 0) com.mojang.blaze3d.systems.RenderSystem.colorMask(false, false, false, false);
            else com.mojang.blaze3d.systems.RenderSystem.colorMask(true, true, true, true);
            cache.buffer.drawWithShader(pose.last().pose(), projection, com.mojang.blaze3d.systems.RenderSystem.getShader());
            ^///?}
        }
        com.mojang.blaze3d.vertex.VertexBuffer.unbind();
        pose.popPose();
        layersDrawn++;
    }

    //? if <1.21 {
    /^private static final net.minecraft.resources.ResourceLocation CLOUDS =
            new net.minecraft.resources.ResourceLocation("textures/environment/clouds.png");
    ^///?}

    /^*
     * Vanilla's {@code buildClouds} (the 1.7 geometry, which hbm-space's {@code CloudProviderCelestial} kept for each
     * layer), with the layer's colour and alpha in place of {@code getCloudColor} and 0.8.
     ^/
    private static void build(Sink sink, net.minecraft.client.CloudStatus status, double d2, double d3, double d4,
                              Clouds.Layer layer) {
        float f3 = (float) net.minecraft.util.Mth.floor(d2) * 0.00390625F;
        float f4 = (float) net.minecraft.util.Mth.floor(d4) * 0.00390625F;
        float f5 = layer.red();
        float f6 = layer.green();
        float f7 = layer.blue();
        float a = layer.alpha();
        float f8 = f5 * 0.9F, f9 = f6 * 0.9F, f10 = f7 * 0.9F;
        float f11 = f5 * 0.7F, f12 = f6 * 0.7F, f13 = f7 * 0.7F;
        float f14 = f5 * 0.8F, f15 = f6 * 0.8F, f16 = f7 * 0.8F;
        float f17 = (float) Math.floor(d3 / 4.0) * 4.0F;
        float t = 0.00390625F;
        if (status == net.minecraft.client.CloudStatus.FANCY) {
            for (int k = -3; k <= 4; k++) {
                for (int l = -3; l <= 4; l++) {
                    float x = (float) (k * 8);
                    float z = (float) (l * 8);
                    if (f17 > -5.0F) {
                        sink.vertex(x, f17, z + 8, x * t + f3, (z + 8) * t + f4, f11, f12, f13, a, 0, -1, 0);
                        sink.vertex(x + 8, f17, z + 8, (x + 8) * t + f3, (z + 8) * t + f4, f11, f12, f13, a, 0, -1, 0);
                        sink.vertex(x + 8, f17, z, (x + 8) * t + f3, z * t + f4, f11, f12, f13, a, 0, -1, 0);
                        sink.vertex(x, f17, z, x * t + f3, z * t + f4, f11, f12, f13, a, 0, -1, 0);
                    }
                    if (f17 <= 5.0F) {
                        float y = f17 + 4.0F - 9.765625E-4F;
                        sink.vertex(x, y, z + 8, x * t + f3, (z + 8) * t + f4, f5, f6, f7, a, 0, 1, 0);
                        sink.vertex(x + 8, y, z + 8, (x + 8) * t + f3, (z + 8) * t + f4, f5, f6, f7, a, 0, 1, 0);
                        sink.vertex(x + 8, y, z, (x + 8) * t + f3, z * t + f4, f5, f6, f7, a, 0, 1, 0);
                        sink.vertex(x, y, z, x * t + f3, z * t + f4, f5, f6, f7, a, 0, 1, 0);
                    }
                    if (k > -1) {
                        for (int n = 0; n < 8; n++) {
                            float u = (x + n + 0.5F) * t + f3;
                            sink.vertex(x + n, f17, z + 8, u, (z + 8) * t + f4, f8, f9, f10, a, -1, 0, 0);
                            sink.vertex(x + n, f17 + 4, z + 8, u, (z + 8) * t + f4, f8, f9, f10, a, -1, 0, 0);
                            sink.vertex(x + n, f17 + 4, z, u, z * t + f4, f8, f9, f10, a, -1, 0, 0);
                            sink.vertex(x + n, f17, z, u, z * t + f4, f8, f9, f10, a, -1, 0, 0);
                        }
                    }
                    if (k <= 1) {
                        for (int n = 0; n < 8; n++) {
                            float u = (x + n + 0.5F) * t + f3;
                            float px = x + n + 1.0F - 9.765625E-4F;
                            sink.vertex(px, f17, z + 8, u, (z + 8) * t + f4, f8, f9, f10, a, 1, 0, 0);
                            sink.vertex(px, f17 + 4, z + 8, u, (z + 8) * t + f4, f8, f9, f10, a, 1, 0, 0);
                            sink.vertex(px, f17 + 4, z, u, z * t + f4, f8, f9, f10, a, 1, 0, 0);
                            sink.vertex(px, f17, z, u, z * t + f4, f8, f9, f10, a, 1, 0, 0);
                        }
                    }
                    if (l > -1) {
                        for (int n = 0; n < 8; n++) {
                            float v = (z + n + 0.5F) * t + f4;
                            sink.vertex(x, f17 + 4, z + n, x * t + f3, v, f14, f15, f16, a, 0, 0, -1);
                            sink.vertex(x + 8, f17 + 4, z + n, (x + 8) * t + f3, v, f14, f15, f16, a, 0, 0, -1);
                            sink.vertex(x + 8, f17, z + n, (x + 8) * t + f3, v, f14, f15, f16, a, 0, 0, -1);
                            sink.vertex(x, f17, z + n, x * t + f3, v, f14, f15, f16, a, 0, 0, -1);
                        }
                    }
                    if (l <= 1) {
                        for (int n = 0; n < 8; n++) {
                            float v = (z + n + 0.5F) * t + f4;
                            float pz = z + n + 1.0F - 9.765625E-4F;
                            sink.vertex(x, f17 + 4, pz, x * t + f3, v, f14, f15, f16, a, 0, 0, 1);
                            sink.vertex(x + 8, f17 + 4, pz, (x + 8) * t + f3, v, f14, f15, f16, a, 0, 0, 1);
                            sink.vertex(x + 8, f17, pz, (x + 8) * t + f3, v, f14, f15, f16, a, 0, 0, 1);
                            sink.vertex(x, f17, pz, x * t + f3, v, f14, f15, f16, a, 0, 0, 1);
                        }
                    }
                }
            }
        } else {
            for (int l1 = -32; l1 < 32; l1 += 32) {
                for (int i2 = -32; i2 < 32; i2 += 32) {
                    sink.vertex(l1, f17, i2 + 32, l1 * t + f3, (i2 + 32) * t + f4, f5, f6, f7, a, 0, -1, 0);
                    sink.vertex(l1 + 32, f17, i2 + 32, (l1 + 32) * t + f3, (i2 + 32) * t + f4, f5, f6, f7, a, 0, -1, 0);
                    sink.vertex(l1 + 32, f17, i2, (l1 + 32) * t + f3, i2 * t + f4, f5, f6, f7, a, 0, -1, 0);
                    sink.vertex(l1, f17, i2, l1 * t + f3, i2 * t + f4, f5, f6, f7, a, 0, -1, 0);
                }
            }
        }
    }
    *///?}
}
