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
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
*///?}

/**
 * HBM 1.12's screen-space heat distortion ({@code HbmShaderManager2.distort} + {@code heatDistortion()}): geometry
 * accumulates a distortion amount into a full-size layer during the hand pass, and {@link ScreenEffects#afterHand()}
 * then redraws the frame through {@code heat_distortion_post}, offsetting each pixel by animated noise scaled by
 * that amount.
 *
 * <p>This is not {@link HeatDistortionRenderType}. That one samples the scene inside the particle's own draw, which
 * only sees what was drawn before it; this effect's shell surrounds the geometry it distorts (the crucible blade),
 * so it has to run after the hand is complete, over the finished frame, as 1.12 did.
 */
public final class ScreenDistortion {

    static final ScreenEffects.Layer LAYER = new ScreenEffects.Layer("golem_distortion_layer");

    //? if >=26.1 {
    private static Identifier noise;
    //?} else {
    /*private static ResourceLocation noise;
    *///?}

    private ScreenDistortion() {}

    /**
     * Geometry into the distortion layer, as 1.12's {@code distort(amount, ...)} drew it: red =
     * {@code amount * smoothstep(0, 1, mask alpha)}, where {@code amount} is the vertex colour's red channel,
     * accumulated {@code ONE / ONE}, depth-tested against the main depth without writing it, back faces culled.
     * {@code noise} is the texture the composite animates the offset with (1.12 bound {@code noise_2}).
     */
    //? if >=26.1 {
    public static RenderType of(Identifier mask, Identifier noise) {
        ScreenDistortion.noise = noise;
        return BY_MASK.computeIfAbsent(mask, ScreenDistortion::create);
    }

    private static final Map<Identifier, RenderType> BY_MASK = new HashMap<>();

    private static final OutputTarget OUTPUT = new OutputTarget("golem_distortion_layer", LAYER::drawTarget);

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(dev.golem.core.Ids.of("golemcore", "pipeline/screen_distortion_layer"))
            .withVertexShader(dev.golem.core.Ids.of("golemcore", "core/screen_layer"))
            .withFragmentShader(dev.golem.core.Ids.of("golemcore", "core/screen_distortion_layer"))
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withCull(true)
            .build();

    private static RenderType create(Identifier mask) {
        return RenderType.create("golem_distortion_layer", RenderSetup.builder(PIPELINE)
                .withTexture("Sampler0", mask)
                .setOutputTarget(OUTPUT)
                .createRenderSetup());
    }

    private static final RenderPipeline COMPOSITE = ScreenEffects.fullscreenPipeline("screen_distortion", 3, Optional.empty());
    private static com.mojang.blaze3d.pipeline.TextureTarget scene;

    static void apply() {
        if (!LAYER.consumeUsed()) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (scene == null || scene.width != main.width || scene.height != main.height) {
            if (scene != null) scene.destroyBuffers();
            scene = ScreenEffects.newTarget("golem_distortion_scene", main.width, main.height);
        }
        // 1.12 read the framebuffer it was writing to; sample a detached copy instead.
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main.getColorTexture(),
                scene.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
        var noiseTexture = Minecraft.getInstance().getTextureManager().getTexture(noise);
        RenderTarget layer = LAYER.target();
        ScreenEffects.fullscreen("Screen distortion", COMPOSITE, main.getColorTextureView(), ScreenEffects.wallClockSeconds(),
                pass -> {
                    pass.bindTexture("Sampler0", layer.getColorTextureView(), ScreenEffects.linearClamp());
                    // noise_2: 1.12 set GL_LINEAR magnification on it; the texture manager's wrap is repeat
                    pass.bindTexture("Sampler1", noiseTexture.getTextureView(),
                            RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
                    // the main framebuffer texture is GL_NEAREST in 1.12
                    pass.bindTexture("Sampler2", scene.getColorTextureView(),
                            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                });
        ScreenEffects.report("distortion", LAYER.target());
        LAYER.clear();
    }
    //?} else {
    /*public static RenderType of(ResourceLocation mask, ResourceLocation noise) {
        ScreenDistortion.noise = noise;
        return BY_MASK.computeIfAbsent(mask, Factory::distortion);
    }

    private static final Map<ResourceLocation, RenderType> BY_MASK = new HashMap<>();
    private static net.minecraft.client.renderer.ShaderInstance shader;
    private static com.mojang.blaze3d.pipeline.TextureTarget scene;

    static void resetShaders() {
        if (shader != null) shader.close();
        shader = null;
    }

    private static net.minecraft.client.renderer.ShaderInstance shader() {
        if (shader == null) {
            try {
                // 1.20.1's apply() re-applies the program JSON's blend (none: opaque); put ONE / ONE back at the draw.
                shader = ShaderLoader.load(Minecraft.getInstance().getResourceManager(), "golemcore:screen_distortion_layer",
                        DefaultVertexFormat.NEW_ENTITY, () -> {
                            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                            com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                                    com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
                        });
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not load distortion layer shader", exception);
            }
        }
        return shader;
    }

    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        private static final OutputStateShard OUTPUT = new OutputStateShard("golem_distortion_layer",
                LAYER::bindForDraw, () -> Minecraft.getInstance().getMainRenderTarget().bindWrite(false));

        static RenderType distortion(ResourceLocation mask) {
            return create("golem_distortion_layer", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(ScreenDistortion::shader))
                            .setTextureState(new TextureStateShard(mask, false, false))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(OUTPUT)
                            .createCompositeState(false));
        }
    }

    static void apply() {
        if (!LAYER.consumeUsed()) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        try (ScreenEffects.SavedState ignored = new ScreenEffects.SavedState()) {
            if (scene == null || scene.width != main.width || scene.height != main.height) {
                if (scene != null) scene.destroyBuffers();
                scene = ScreenEffects.newTarget(main.width, main.height, org.lwjgl.opengl.GL11.GL_NEAREST);
            }
            // 1.12 read the framebuffer it was writing to; sample a detached copy instead. bindRead only binds a
            // texture, so select main as the read framebuffer explicitly.
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            com.mojang.blaze3d.systems.RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            com.mojang.blaze3d.systems.RenderSystem.bindTexture(scene.getColorTextureId());
            com.mojang.blaze3d.platform.GlStateManager._glCopyTexSubImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                    main.width, main.height);
            var noiseTexture = Minecraft.getInstance().getTextureManager().getTexture(noise);
            // noise_2: 1.12 set GL_LINEAR magnification on it; the texture manager's wrap is repeat
            noiseTexture.setFilter(true, false);
            ScreenEffects.fullscreen("screen_distortion", main, false, ScreenEffects.wallClockSeconds(),
                    LAYER.target().getColorTextureId(), noiseTexture.getId(), scene.getColorTextureId());
            ScreenEffects.report("distortion", LAYER.target());
            LAYER.clear();
        }
    }
    *///?}
}
