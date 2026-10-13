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
 * <p>The chain is {@code HbmShaderManager2.bloom()} pass for pass, with one change: level 0 is half the
 * screen size (for the GPU cost; 1.12 ran it at full size). Level sizes are {@code W/2},
 * {@code W/4}, {@code W/16}, {@code W/32} (levels 1..3 are 1.12's: it multiplied by 0.25 twice, then 0.5). The layer
 * is box-downsampled into level 0 and each level from the previous one; then from the smallest level up, each level
 * is blurred horizontally and vertically, the vertical result combined into the next-larger level by maximum, and
 * level 0's result added (ONE / ONE, alpha untouched) over the frame.
 *
 * <p>The half-size level 0 keeps 1.12's on-screen kernel: its blurs step half a texel (one screen pixel), and level 1
 * is taken from it with a 2x2 average, so level 1 keeps its 4x4 screen-pixel footprint. What is lost is detail finer
 * than two screen pixels in the innermost glow; brightness is conserved (every kernel sums to 1).
 *
 * <p>1.12 blended the maximum with {@code GL_MAX}; 26.1 has no blend-equation control, so every era reads the
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
    private static final RenderPipeline DOWNSAMPLE_FINE = ScreenEffects.fullscreenPipeline("screen_downsample_fine", 1, Optional.empty());
    private static final RenderPipeline BLUR_H = ScreenEffects.fullscreenPipeline("screen_blur_h", 1, Optional.empty());
    private static final RenderPipeline BLUR_H_FINE = ScreenEffects.fullscreenPipeline("screen_blur_h_fine", 1, Optional.empty());
    private static final RenderPipeline BLUR_V_FINE = ScreenEffects.fullscreenPipeline("screen_blur_v_fine", 1, Optional.empty());
    private static final RenderPipeline BLUR_V_MAX = ScreenEffects.fullscreenPipeline("screen_blur_v_max", 2, Optional.empty());
    private static final RenderPipeline UPSAMPLE_ADD = ScreenEffects.fullscreenPipeline("screen_upsample", 1,
            Optional.of(BlendFunction.ADDITIVE));

    private static final com.mojang.blaze3d.pipeline.TextureTarget[] DATA = new com.mojang.blaze3d.pipeline.TextureTarget[LEVELS];
    private static final com.mojang.blaze3d.pipeline.TextureTarget[] SPARE = new com.mojang.blaze3d.pipeline.TextureTarget[LEVELS];
    private static int chainWidth = -1;
    private static int chainHeight = -1;

    /** (Re)builds the level targets when the screen size changed (a window resize; the GUI scale does not resize them). */
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
        logChain(main.width, main.height, sizes);
    }

    private static void pass(String label, RenderPipeline pipeline, RenderTarget output, RenderTarget source, float time) {
        var sampler = ScreenEffects.linearClamp();
        ScreenEffects.fullscreen(label, pipeline, output.getColorTextureView(), time,
                renderPass -> renderPass.bindTexture("Sampler0", source.getColorTextureView(), sampler));
    }

    static void apply() {
        if (!LAYER.consumeUsed()) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        ensureChain(main);
        var sampler = ScreenEffects.linearClamp();
        float time = ScreenEffects.wallClockSeconds();

        pass("Bloom downsample", DOWNSAMPLE, DATA[0], LAYER.target(), time);
        pass("Bloom downsample", DOWNSAMPLE_FINE, DATA[1], DATA[0], time);
        for (int level = 2; level < LEVELS; level++) {
            pass("Bloom downsample", DOWNSAMPLE, DATA[level], DATA[level - 1], time);
        }
        for (int level = LEVELS - 1; level >= 1; level--) {
            RenderTarget blurred = SPARE[level];
            pass("Bloom blur", BLUR_H, blurred, DATA[level], time);
            RenderTarget destination = DATA[level - 1];
            RenderTarget combined = SPARE[level - 1];
            ScreenEffects.fullscreen("Bloom blur", BLUR_V_MAX, combined.getColorTextureView(), time, renderPass -> {
                renderPass.bindTexture("Sampler0", blurred.getColorTextureView(), sampler);
                renderPass.bindTexture("Sampler1", destination.getColorTextureView(), sampler);
            });
            swap(level - 1);
        }
        pass("Bloom blur", BLUR_H_FINE, SPARE[0], DATA[0], time);
        pass("Bloom blur", BLUR_V_FINE, DATA[0], SPARE[0], time);
        pass("Bloom composite", UPSAMPLE_ADD, main, DATA[0], time);
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
            return ScreenEffects.skipEmpty(create("golem_bloom_layer", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(Bloom::shader))
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(OUTPUT)
                            .createCompositeState(false)), false);
        }
    }

    private static final com.mojang.blaze3d.pipeline.TextureTarget[] DATA = new com.mojang.blaze3d.pipeline.TextureTarget[LEVELS];
    private static final com.mojang.blaze3d.pipeline.TextureTarget[] SPARE = new com.mojang.blaze3d.pipeline.TextureTarget[LEVELS];
    private static int chainWidth = -1;
    private static int chainHeight = -1;

    /^* (Re)builds the level targets when the screen size changed (a window resize; the GUI scale does not resize them). ^/
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
        logChain(main.width, main.height, sizes);
    }

    static void apply() {
        if (!LAYER.consumeUsed()) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        try (ScreenEffects.SavedState ignored = new ScreenEffects.SavedState()) {
            ensureChain(main);
            float time = ScreenEffects.wallClockSeconds();
            ScreenEffects.fullscreen("screen_downsample", DATA[0], false, time, LAYER.target().getColorTextureId());
            ScreenEffects.fullscreen("screen_downsample_fine", DATA[1], false, time, DATA[0].getColorTextureId());
            for (int level = 2; level < LEVELS; level++) {
                ScreenEffects.fullscreen("screen_downsample", DATA[level], false, time, DATA[level - 1].getColorTextureId());
            }
            for (int level = LEVELS - 1; level >= 1; level--) {
                ScreenEffects.fullscreen("screen_blur_h", SPARE[level], false, time, DATA[level].getColorTextureId());
                ScreenEffects.fullscreen("screen_blur_v_max", SPARE[level - 1], false, time,
                        SPARE[level].getColorTextureId(), DATA[level - 1].getColorTextureId());
                swap(level - 1);
            }
            ScreenEffects.fullscreen("screen_blur_h_fine", SPARE[0], false, time, DATA[0].getColorTextureId());
            ScreenEffects.fullscreen("screen_blur_v_fine", DATA[0], false, time, SPARE[0].getColorTextureId());
            ScreenEffects.fullscreen("screen_upsample", main, true, time, DATA[0].getColorTextureId());
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

    /** The screen size the chain was last built for, then each level's size; empty before the first composite. */
    static int[] chain() {
        if (chainWidth < 0) return new int[0];
        int[][] sizes = levelSizes(chainWidth, chainHeight);
        int[] out = new int[2 + 2 * LEVELS];
        out[0] = chainWidth;
        out[1] = chainHeight;
        for (int level = 0; level < LEVELS; level++) {
            out[2 + 2 * level] = sizes[level][0];
            out[3 + 2 * level] = sizes[level][1];
        }
        return out;
    }

    /** Logs each (re)build of the chain: the positive signal that level 0 is half the screen and follows resizes. */
    private static void logChain(int width, int height, int[][] sizes) {
        dev.golem.core.GolemCore.LOG.info("Bloom chain for {}x{}: levels {}x{}, {}x{}, {}x{}, {}x{}", width, height,
                sizes[0][0], sizes[0][1], sizes[1][0], sizes[1][1], sizes[2][0], sizes[2][1], sizes[3][0], sizes[3][1]);
    }

    /**
     * Level 0 is half the screen, rounded up so an odd size loses no column or row (the passes address texels by
     * normalised coordinates, so the sub-texel stretch this leaves is spread evenly over the screen); levels 1..3 are
     * 1.12's ({@code bloomW = width}; per level {@code (int) bloomW}, then {@code *= 0.25} for the first two,
     * {@code 0.5} after).
     */
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
        sizes[0][0] = Math.max(1, (width + 1) / 2);
        sizes[0][1] = Math.max(1, (height + 1) / 2);
        return sizes;
    }
}
