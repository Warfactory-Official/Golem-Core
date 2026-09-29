package dev.golem.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.Optional;
import java.util.function.Function;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;

import java.util.function.Function;
*///?}

/**
 * HBM 1.12's bloom: geometry drawn into a full-size layer during the hand pass, then blurred through a four-level
 * chain and added over the frame by {@link ScreenEffects#afterHand()}.
 *
 * <p>The chain is {@code HbmShaderManager2.bloom()} pass for pass. Level sizes are {@code W}, {@code W/4},
 * {@code W/16}, {@code W/32} (1.12 multiplied by 0.25 twice, then 0.5). The layer is box-downsampled into level 0
 * and each level from the previous one; then from the smallest level up, each level is blurred horizontally and
 * vertically, the vertical result combined into the next-larger level by maximum, and level 0's vertical result
 * added (ONE / ONE, alpha untouched) over the frame. The only restatement is the maximum: 1.12 blended it with
 * {@code GL_MAX} into the destination level; 26.1 has no blend-equation control, so every era reads the
 * destination in the shader and writes the maximum into that level's spare target, swapping the two.
 */
public final class Bloom {

    static final ScreenEffects.Layer LAYER = new ScreenEffects.Layer("golem_bloom_layer");
    private static final int LEVELS = 4;

    private Bloom() {}

    /**
     * Textured geometry into the bloom layer, as 1.12 drew into {@code bloomData}: texture times vertex colour,
     * alpha-tested ({@code > 0.1}), blended {@code SRC_ALPHA / ONE}, depth-tested against the main depth without
     * writing it, back faces culled. Submit it after the surface it glows over.
     */
    //? if >=26.1 {
    public static RenderType of(Identifier texture) {
        return BY_TEXTURE.apply(texture);
    }

    private static final OutputTarget OUTPUT = new OutputTarget("golem_bloom_layer", LAYER::drawTarget);

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(dev.golem.core.Ids.of("golemcore", "pipeline/screen_bloom_layer"))
            .withVertexShader(dev.golem.core.Ids.of("golemcore", "core/screen_layer"))
            .withFragmentShader(dev.golem.core.Ids.of("golemcore", "core/screen_bloom_layer"))
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withCull(true)
            .build();

    private static final Function<Identifier, RenderType> BY_TEXTURE = Util.memoize(texture ->
            RenderType.create("golem_bloom_layer", RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", texture)
                    .setOutputTarget(OUTPUT)
                    .createRenderSetup()));

    private static final RenderPipeline DOWNSAMPLE = ScreenEffects.fullscreenPipeline("screen_downsample", 1, Optional.empty());
    private static final RenderPipeline BLUR_H = ScreenEffects.fullscreenPipeline("screen_blur_h", 1, Optional.empty());
    private static final RenderPipeline BLUR_V_MAX = ScreenEffects.fullscreenPipeline("screen_blur_v_max", 2, Optional.empty());
    private static final RenderPipeline BLUR_V_ADD = ScreenEffects.fullscreenPipeline("screen_blur_v", 1,
            Optional.of(BlendFunction.ADDITIVE));

    private static final com.mojang.blaze3d.pipeline.TextureTarget[] DATA = new com.mojang.blaze3d.pipeline.TextureTarget[LEVELS];
    private static final com.mojang.blaze3d.pipeline.TextureTarget[] SPARE = new com.mojang.blaze3d.pipeline.TextureTarget[LEVELS];
    private static int chainWidth = -1;
    private static int chainHeight = -1;

    private static void ensureChain(RenderTarget main) {
        if (chainWidth == main.width && chainHeight == main.height && DATA[0] != null) return;
        for (int level = 0; level < LEVELS; level++) {
            if (DATA[level] != null) DATA[level].destroyBuffers();
            if (SPARE[level] != null) SPARE[level].destroyBuffers();
        }
        int[][] sizes = levelSizes(main.width, main.height);
        for (int level = 0; level < LEVELS; level++) {
            DATA[level] = ScreenEffects.newTarget("golem_bloom_" + level + "a", sizes[level][0], sizes[level][1]);
            SPARE[level] = ScreenEffects.newTarget("golem_bloom_" + level + "b", sizes[level][0], sizes[level][1]);
        }
        chainWidth = main.width;
        chainHeight = main.height;
    }

    static void apply() {
        if (!LAYER.consumeUsed()) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        ensureChain(main);
        var sampler = ScreenEffects.linearClamp();
        float time = ScreenEffects.wallClockSeconds();

        RenderTarget layer = LAYER.target();
        ScreenEffects.fullscreen("Bloom downsample", DOWNSAMPLE, DATA[0].getColorTextureView(), time,
                pass -> pass.bindTexture("Sampler0", layer.getColorTextureView(), sampler));
        for (int level = 1; level < LEVELS; level++) {
            RenderTarget source = DATA[level - 1];
            ScreenEffects.fullscreen("Bloom downsample", DOWNSAMPLE, DATA[level].getColorTextureView(), time,
                    pass -> pass.bindTexture("Sampler0", source.getColorTextureView(), sampler));
        }
        for (int level = LEVELS - 1; level >= 0; level--) {
            RenderTarget data = DATA[level];
            RenderTarget blurred = SPARE[level];
            ScreenEffects.fullscreen("Bloom blur", BLUR_H, blurred.getColorTextureView(), time,
                    pass -> pass.bindTexture("Sampler0", data.getColorTextureView(), sampler));
            if (level == 0) {
                ScreenEffects.fullscreen("Bloom composite", BLUR_V_ADD, main.getColorTextureView(), time,
                        pass -> pass.bindTexture("Sampler0", blurred.getColorTextureView(), sampler));
            } else {
                RenderTarget destination = DATA[level - 1];
                RenderTarget combined = SPARE[level - 1];
                ScreenEffects.fullscreen("Bloom blur", BLUR_V_MAX, combined.getColorTextureView(), time, pass -> {
                    pass.bindTexture("Sampler0", blurred.getColorTextureView(), sampler);
                    pass.bindTexture("Sampler1", destination.getColorTextureView(), sampler);
                });
                swap(level - 1);
            }
        }
        ScreenEffects.report("bloom", LAYER.target());
        LAYER.clear();
    }
    //?} else {
    /*public static RenderType of(ResourceLocation texture) {
        return BY_TEXTURE.apply(texture);
    }

    private static final Function<ResourceLocation, RenderType> BY_TEXTURE = Util.memoize(Factory::bloom);
    private static net.minecraft.client.renderer.ShaderInstance shader;

    static void resetShaders() {
        if (shader != null) shader.close();
        shader = null;
    }

    private static net.minecraft.client.renderer.ShaderInstance shader() {
        if (shader == null) {
            try {
                // 1.20.1's apply() re-applies the program JSON's blend (none: opaque) after the render type set its
                // own; put SRC_ALPHA / ONE back at the draw, as PlasmaGlowRenderType does.
                shader = ShaderLoader.load(Minecraft.getInstance().getResourceManager(), "golemcore:screen_bloom_layer",
                        DefaultVertexFormat.NEW_ENTITY, () -> {
                            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                            com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                                    com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
                        });
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not load bloom layer shader", exception);
            }
        }
        return shader;
    }

    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        private static final OutputStateShard OUTPUT = new OutputStateShard("golem_bloom_layer",
                LAYER::bindForDraw, () -> Minecraft.getInstance().getMainRenderTarget().bindWrite(false));

        static RenderType bloom(ResourceLocation texture) {
            return create("golem_bloom_layer", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(Bloom::shader))
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(OUTPUT)
                            .createCompositeState(false));
        }
    }

    private static final com.mojang.blaze3d.pipeline.TextureTarget[] DATA = new com.mojang.blaze3d.pipeline.TextureTarget[LEVELS];
    private static final com.mojang.blaze3d.pipeline.TextureTarget[] SPARE = new com.mojang.blaze3d.pipeline.TextureTarget[LEVELS];
    private static int chainWidth = -1;
    private static int chainHeight = -1;

    private static void ensureChain(RenderTarget main) {
        if (chainWidth == main.width && chainHeight == main.height && DATA[0] != null) return;
        for (int level = 0; level < LEVELS; level++) {
            if (DATA[level] != null) DATA[level].destroyBuffers();
            if (SPARE[level] != null) SPARE[level].destroyBuffers();
        }
        int[][] sizes = levelSizes(main.width, main.height);
        for (int level = 0; level < LEVELS; level++) {
            DATA[level] = ScreenEffects.newTarget(sizes[level][0], sizes[level][1], org.lwjgl.opengl.GL11.GL_LINEAR);
            SPARE[level] = ScreenEffects.newTarget(sizes[level][0], sizes[level][1], org.lwjgl.opengl.GL11.GL_LINEAR);
        }
        chainWidth = main.width;
        chainHeight = main.height;
    }

    static void apply() {
        if (!LAYER.consumeUsed()) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        try (ScreenEffects.SavedState ignored = new ScreenEffects.SavedState()) {
            ensureChain(main);
            float time = ScreenEffects.wallClockSeconds();
            ScreenEffects.fullscreen("screen_downsample", DATA[0], false, time, LAYER.target().getColorTextureId());
            for (int level = 1; level < LEVELS; level++) {
                ScreenEffects.fullscreen("screen_downsample", DATA[level], false, time, DATA[level - 1].getColorTextureId());
            }
            for (int level = LEVELS - 1; level >= 0; level--) {
                ScreenEffects.fullscreen("screen_blur_h", SPARE[level], false, time, DATA[level].getColorTextureId());
                if (level == 0) {
                    ScreenEffects.fullscreen("screen_blur_v", main, true, time, SPARE[0].getColorTextureId());
                } else {
                    ScreenEffects.fullscreen("screen_blur_v_max", SPARE[level - 1], false, time,
                            SPARE[level].getColorTextureId(), DATA[level - 1].getColorTextureId());
                    swap(level - 1);
                }
            }
            ScreenEffects.report("bloom", LAYER.target());
            LAYER.clear();
        }
    }
    *///?}

    /** The combined level now lives in the spare target: make it the level's data. */
    private static void swap(int level) {
        var data = DATA[level];
        DATA[level] = SPARE[level];
        SPARE[level] = data;
    }

    /** 1.12: {@code bloomW = width}; per level {@code (int) bloomW}, then {@code *= 0.25} for the first two, {@code 0.5} after. */
    static int[][] levelSizes(int width, int height) {
        int[][] sizes = new int[LEVELS][2];
        float w = width;
        float h = height;
        for (int level = 0; level < LEVELS; level++) {
            sizes[level][0] = Math.max(1, (int) w);
            sizes[level][1] = Math.max(1, (int) h);
            float scale = level < 2 ? 0.25F : 0.5F;
            w *= scale;
            h *= scale;
        }
        return sizes;
    }
}
