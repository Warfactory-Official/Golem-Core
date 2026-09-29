package dev.golem.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;

/**
 * Screen-space layers that first-person geometry is drawn into while the hand renders, composited over the
 * frame once the hand is finished.
 *
 * <p>This is HBM 1.12's {@code HbmShaderManager2}: a renderer bound {@code bloomData} or {@code distortionBuffer}
 * (both full-size, both sharing the main framebuffer's depth attachment), drew into it, and bound the main
 * framebuffer again; {@code postProcess()} ran from the hand event and composited {@link Bloom} and then
 * {@link ScreenDistortion}. Here a layer is the output target of a render type, so geometry reaches it through
 * the ordinary submit path on every version, and {@link #afterHand()} is the {@code postProcess()} call.
 *
 * <p>The layers and bloom chain are 8-bit RGBA (1.12 used RGBA16F). 26.1's texture API offers no float colour
 * format, and one format everywhere keeps every node's pixels the same. Every value in the chain stays within
 * [0, 1] for the textures drawn into it (the gaussian weights sum to 1, downsampling averages, the combine is a
 * maximum), so only the faintest tail of the halo is quantised.
 */
public final class ScreenEffects {

    private static boolean initialized;

    private ScreenEffects() {}

    /** Register during client initialization, before the loader dispatches its reload-registration event. */
    public static void init() {
        if (initialized) return;
        initialized = true;
        dev.golem.core.client.ClientResourceReloads.register("golemcore", "screen_effects", () -> {
            //? if <26.1 {
            /*Programs.reset();
            Bloom.resetShaders();
            ScreenDistortion.resetShaders();
            *///?}
        });
    }

    /**
     * Composites the layers drawn this frame, bloom first, then distortion (1.12's order), and clears them.
     * Called once the first-person hand has been drawn; see {@code MixinScreenEffects}.
     */
    public static void afterHand() {
        Bloom.apply();
        ScreenDistortion.apply();
    }

    private static final java.util.Set<String> REPORTED = new java.util.HashSet<>();

    /** Logs the first composite of each effect: the positive signal that the layer was drawn and applied. */
    static void report(String effect, RenderTarget layer) {
        if (REPORTED.add(effect)) {
            dev.golem.core.GolemCore.LOG.info("Screen effect composited after the hand: {} ({}x{})", effect,
                    layer.width, layer.height);
        }
    }

    static float wallClockSeconds() {
        return (System.currentTimeMillis() % 10_000_000L) / 1000F;
    }

    /** A full-size colour layer that depth-tests against the main target's depth, as 1.12's shared attachment did. */
    static final class Layer {
        private final String label;
        private boolean used;
        //? if >=26.1 {
        private SharedDepthTarget target;
        //?} else {
        /*private com.mojang.blaze3d.pipeline.TextureTarget target;
        *///?}

        Layer(String label) {
            this.label = label;
        }

        /** Whether geometry was drawn into this layer since the last call; resets the flag. */
        boolean consumeUsed() {
            boolean wasUsed = used && target != null;
            used = false;
            return wasUsed;
        }

        RenderTarget target() {
            return target;
        }

        //? if >=26.1 {
        /** The target a render type draws into this frame; marks the layer used. */
        RenderTarget drawTarget() {
            used = true;
            return ensure();
        }

        private RenderTarget ensure() {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            if (target == null || target.width != main.width || target.height != main.height) {
                if (target != null) target.destroyBuffers();
                target = new SharedDepthTarget(label, main.width, main.height);
                clear();
            }
            return target;
        }

        void clear() {
            if (target != null) {
                RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), 0);
            }
        }
        //?} else {
        /*/^* Binds the layer (with the main depth attached) for a render type's draw; marks the layer used. ^/
        void bindForDraw() {
            used = true;
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            ensure(main);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, target.frameBufferId);
            // The main depth texture is replaced on resize; attaching it every draw keeps the layer complete.
            com.mojang.blaze3d.platform.GlStateManager._glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER,
                    org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT, org.lwjgl.opengl.GL11.GL_TEXTURE_2D, main.getDepthTextureId(), 0);
        }

        private void ensure(RenderTarget main) {
            if (target == null || target.width != main.width || target.height != main.height) {
                if (target != null) target.destroyBuffers();
                target = newTarget(main.width, main.height, org.lwjgl.opengl.GL11.GL_LINEAR);
                clear();
            }
        }

        void clear() {
            if (target != null) clearColor(target);
        }
        *///?}
    }

    //? if >=26.1 {
    /**
     * A layer target whose depth attachment is the main target's. {@code RenderType.draw} attaches
     * {@code getDepthTextureView()} of its output target, so this is all it takes for the layer to be tested
     * against the hand's depth. Its own depth texture is allocated by the superclass and never used.
     */
    private static final class SharedDepthTarget extends com.mojang.blaze3d.pipeline.TextureTarget {
        SharedDepthTarget(String label, int width, int height) {
            super(label, width, height, true);
        }

        @Override
        public com.mojang.blaze3d.textures.GpuTextureView getDepthTextureView() {
            return Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        }
    }

    /** A plain colour target for a chain pass. */
    static com.mojang.blaze3d.pipeline.TextureTarget newTarget(String label, int width, int height) {
        com.mojang.blaze3d.pipeline.TextureTarget target =
                new com.mojang.blaze3d.pipeline.TextureTarget(label, Math.max(1, width), Math.max(1, height), false);
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), 0);
        return target;
    }

    /** A fullscreen pass: {@code pipeline} draws the screen triangle into {@code output}. */
    static void fullscreen(String label, com.mojang.blaze3d.pipeline.RenderPipeline pipeline,
                           com.mojang.blaze3d.textures.GpuTextureView output, float time,
                           java.util.function.Consumer<com.mojang.blaze3d.systems.RenderPass> samplers) {
        var builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        builder.addVertex(-1F, -1F, time);
        builder.addVertex(3F, -1F, time);
        builder.addVertex(-1F, 3F, time);
        try (var mesh = builder.buildOrThrow()) {
            var vertices = DefaultVertexFormat.POSITION.uploadImmediateVertexBuffer(mesh.vertexBuffer());
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> label, output, java.util.OptionalInt.empty(), null, java.util.OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                samplers.accept(pass);
                pass.setVertexBuffer(0, vertices);
                pass.draw(0, 3);
            }
        }
    }

    /** A fullscreen pipeline: no depth, colour written without alpha (1.12's colorMask(true, true, true, false)). */
    static com.mojang.blaze3d.pipeline.RenderPipeline fullscreenPipeline(String fragment, int samplers,
            java.util.Optional<com.mojang.blaze3d.pipeline.BlendFunction> blend) {
        var builder = com.mojang.blaze3d.pipeline.RenderPipeline.builder()
                .withLocation(dev.golem.core.Ids.of("golemcore", "pipeline/" + fragment + (blend.isPresent() ? "_add" : "")))
                .withVertexShader(dev.golem.core.Ids.of("golemcore", "core/screen_fullscreen"))
                .withFragmentShader(dev.golem.core.Ids.of("golemcore", "core/" + fragment));
        for (int i = 0; i < samplers; i++) builder.withSampler("Sampler" + i);
        return builder
                .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(java.util.Optional.empty())
                .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(blend,
                        com.mojang.blaze3d.pipeline.ColorTargetState.WRITE_COLOR))
                .withCull(false)
                .build();
    }

    static com.mojang.blaze3d.textures.GpuSampler linearClamp() {
        return RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
    }
    //?} else {
    /*/^* A plain colour target for a chain pass, filtered as given. ^/
    static com.mojang.blaze3d.pipeline.TextureTarget newTarget(int width, int height, int filter) {
        com.mojang.blaze3d.pipeline.TextureTarget target = new com.mojang.blaze3d.pipeline.TextureTarget(
                Math.max(1, width), Math.max(1, height), false, Minecraft.ON_OSX);
        target.setFilterMode(filter);
        clearColor(target);
        return target;
    }

    /^* Clears a target's colour to transparent black, leaving the bound framebuffer and clear colour as they were. ^/
    static void clearColor(RenderTarget target) {
        int drawTarget = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        float[] clear = new float[4];
        org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_COLOR_CLEAR_VALUE, clear);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, target.frameBufferId);
        RenderSystem.clearColor(0F, 0F, 0F, 0F);
        RenderSystem.clear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.clearColor(clear[0], clear[1], clear[2], clear[3]);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, drawTarget);
    }

    /^* The legacy fullscreen programs, loaded on first use and dropped on resource reload. ^/
    static final class Programs {
        private static final java.util.Map<String, net.minecraft.client.renderer.ShaderInstance> LOADED = new java.util.HashMap<>();

        private Programs() {}

        static net.minecraft.client.renderer.ShaderInstance get(String name) {
            return LOADED.computeIfAbsent(name, key -> {
                try {
                    return ShaderLoader.load(Minecraft.getInstance().getResourceManager(), "golemcore:" + key,
                            DefaultVertexFormat.POSITION);
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("Could not load screen-effect shader " + key, exception);
                }
            });
        }

        static void reset() {
            for (net.minecraft.client.renderer.ShaderInstance shader : LOADED.values()) shader.close();
            LOADED.clear();
        }
    }

    /^*
     * The GL state a run of fullscreen passes changes, saved once and put back afterwards. The passes bind their
     * program directly (like {@code DepthMerge}) instead of through {@code ShaderInstance.apply()}, so vanilla's
     * shader and blend caches are left alone.
     ^/
    static final class SavedState implements AutoCloseable {
        private final int program;
        private final int activeTexture;
        private final int[] textures = new int[3];
        private final int[] viewport = new int[4];
        private final boolean[] colorMask = new boolean[4];
        private final boolean blend;
        private final int[] blendFunc = new int[4];
        private final boolean depthTest;
        private final boolean depthWrite;
        private final boolean cull;
        private final boolean scissor;

        SavedState() {
            program = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
            activeTexture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
            for (int i = 0; i < textures.length; i++) {
                RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + i);
                textures[i] = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            }
            RenderSystem.activeTexture(activeTexture);
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                var view = stack.mallocInt(4);
                var mask = stack.malloc(4);
                org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, view);
                org.lwjgl.opengl.GL11.glGetBooleanv(org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK, mask);
                for (int i = 0; i < 4; i++) {
                    viewport[i] = view.get(i);
                    colorMask[i] = mask.get(i) != 0;
                }
            }
            blend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
            blendFunc[0] = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
            blendFunc[1] = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
            blendFunc[2] = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
            blendFunc[3] = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
            depthTest = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            depthWrite = org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
            cull = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
            scissor = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableScissor();
            RenderSystem.colorMask(true, true, true, false);
        }

        @Override
        public void close() {
            com.mojang.blaze3d.platform.GlStateManager._glUseProgram(program);
            for (int i = 0; i < textures.length; i++) {
                RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + i);
                RenderSystem.bindTexture(textures[i]);
            }
            RenderSystem.activeTexture(activeTexture);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            RenderSystem.blendFuncSeparate(blendFunc[0], blendFunc[1], blendFunc[2], blendFunc[3]);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.depthMask(depthWrite);
            if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (scissor) com.mojang.blaze3d.platform.GlStateManager._enableScissorTest();
        }
    }

    /^*
     * A fullscreen pass into {@code output} ({@code additive}: ONE / ONE, otherwise unblended), sampling
     * {@code textures} as Sampler0.. in order. Run inside a {@link SavedState}.
     ^/
    static void fullscreen(String program, RenderTarget output, boolean additive, float time, int... textures) {
        net.minecraft.client.renderer.ShaderInstance shader = Programs.get(program);
        output.bindWrite(true);
        if (additive) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.disableBlend();
        }
        com.mojang.blaze3d.shaders.ProgramManager.glUseProgram(shader.getId());
        for (int i = 0; i < textures.length; i++) {
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + i);
            RenderSystem.bindTexture(textures[i]);
            int location = com.mojang.blaze3d.shaders.Uniform.glGetUniformLocation(shader.getId(), "Sampler" + i);
            com.mojang.blaze3d.shaders.Uniform.uploadInteger(location, i);
        }
        RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        //? if >=1.21 {
        var builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        builder.addVertex(-1F, -1F, time);
        builder.addVertex(3F, -1F, time);
        builder.addVertex(-1F, 3F, time);
        com.mojang.blaze3d.vertex.BufferUploader.draw(builder.buildOrThrow());
        //?} else {
        /^var builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        builder.vertex(-1F, -1F, time).endVertex();
        builder.vertex(3F, -1F, time).endVertex();
        builder.vertex(-1F, 3F, time).endVertex();
        com.mojang.blaze3d.vertex.BufferUploader.draw(builder.end());
        ^///?}
    }
    *///?}
}
