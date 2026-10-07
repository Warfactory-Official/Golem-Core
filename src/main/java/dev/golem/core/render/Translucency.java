package dev.golem.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;

/**
 * What the world's translucent layer (water, stained glass, ice) was drawn over, and how much of each pixel it
 * covers, so a {@link SceneWarp} effect behind water can be drawn under it after the fact.
 *
 * <p>A scene-warp effect is drawn in the late {@link WorldEffects} pass, over the finished scene. Tested against
 * the world's depth there, everything behind water fails (the water's surface is nearer), so an effect seen
 * through water vanished. With this capture it is tested against the depth from before translucency, and where a
 * translucent surface lies in front of it the shader puts that surface back over it: translucent blending is
 * {@code C = W·a + A·(1 - a)} over the scene {@code A} it was drawn on, so over the effect {@code H} it would
 * have been {@code C + (H - A)·(1 - a)}. For several surfaces {@code 1 - a} is their combined transmission,
 * which is exactly what the target's alpha accumulates under vanilla's translucent blend
 * ({@code ONE, ONE_MINUS_SRC_ALPHA} on alpha).
 *
 * <ul>
 *   <li>Fast/Fancy: translucent terrain is drawn into the main target. Just before it, the main colour and depth
 *       are copied ({@link #under}) and the main alpha is cleared to 0; just after it, the main colour (whose alpha
 *       is now the coverage) is copied ({@link #cover}) and the main alpha is put back to exactly what vanilla's
 *       blend would have left ({@code coverage + before·(1 - coverage)}).</li>
 *   <li>Fabulous: translucent terrain has its own target, which already is the layer alone; after it, the opaque
 *       main target and its depth (with item entities') are copied, and the translucent target's colour is the
 *       cover. Nothing in the main target is touched.</li>
 *   <li>Under a shader pack nothing is captured: the pack owns its targets.</li>
 * </ul>
 *
 * <p>Anything opaque drawn into the main target between the two points (outlines, late entity batches) shows in
 * the cover with alpha 1 and in the late depth, so the shader keeps the finished scene there.
 */
final class Translucency {
    private static TextureTarget under;
    private static TextureTarget cover;
    /** Fast/Fancy: the main alpha was cleared this frame and must be put back. */
    private static boolean cleared;
    /** {@link #under} and {@link #cover} hold this frame's capture. */
    private static boolean active;
    private static boolean reportedFast;
    private static boolean reportedFabulous;

    private Translucency() {}

    static boolean active() {
        return active;
    }

    /** Copy-and-colour target holding the scene under the translucent layer, and its depth. */
    static RenderTarget under() {
        return under;
    }

    static RenderTarget cover() {
        return cover;
    }

    /** Puts the main alpha back if the frame cleared it but never reached {@link #after} (still inside the frame). */
    static void settle() {
        if (!cleared) return;
        cleared = false;
        restoreAlpha(Minecraft.getInstance().getMainRenderTarget());
    }

    static void clear() {
        cleared = false;
        active = false;
    }

    static void close() {
        clear();
        if (under != null) under.destroyBuffers();
        if (cover != null) cover.destroyBuffers();
        under = null;
        cover = null;
        reportedFast = false;
        reportedFabulous = false;
        //? if <26.1 {
        /*if (shader != null) shader.close();
        shader = null;
        *///?}
    }

    /** Just before translucent terrain in Fast/Fancy. */
    static void before(RenderTarget main) {
        if (cleared || active) return;
        ensureTargets(main);
        copyColour(main, under);
        under.copyDepthFrom(main);
        //? if >=26.1 {
        alphaPass(main, CLEAR_ALPHA);
        //?} else {
        /*clearAlpha(main);
        *///?}
        cleared = true;
    }

    /** Just after translucent terrain. {@code translucent} is the Fabulous translucent target, else null. */
    static void after(RenderTarget main, RenderTarget translucent, RenderTarget itemEntities) {
        if (active) return;
        if (translucent == null) {
            if (!cleared) return;
            copyColour(main, cover);
            restoreAlpha(main);
            cleared = false;
        } else {
            ensureTargets(main);
            copyColour(main, under);
            DepthMerge.merge(under, main, main, itemEntities);
            copyColour(translucent, cover);
        }
        active = true;
        if (translucent == null ? !reportedFast : !reportedFabulous) {
            if (translucent == null) reportedFast = true;
            else reportedFabulous = true;
            dev.golem.core.GolemCore.LOG.info("Scene warp sees through translucency ({})",
                    translucent == null ? "fast/fancy" : "fabulous");
        }
    }

    private static void restoreAlpha(RenderTarget main) {
        //? if >=26.1 {
        alphaPass(main, RESTORE_ALPHA);
        //?} else {
        /*restoreAlphaLegacy(main);
        *///?}
    }

    private static void ensureTargets(RenderTarget main) {
        if (under == null || under.width != main.width || under.height != main.height) {
            if (under != null) under.destroyBuffers();
            if (cover != null) cover.destroyBuffers();
            //? if >=26.1 {
            under = new TextureTarget("golem_translucency_under", main.width, main.height, true);
            cover = new TextureTarget("golem_translucency_cover", main.width, main.height, false);
            //?} else {
            /*under = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            cover = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            *///?}
        }
    }

    //? if >=26.1 {
    /**
     * The textures a scene-warp shader samples: what lay under the translucent layer, its coverage (alpha) and the
     * world depth after translucency. Without this frame's capture the first two are the scene copy itself, so a
     * shader that picks between them, or composites one over the other, sees no difference.
     */
    static net.minecraft.resources.Identifier underTexture() {
        return View.UNDER.register();
    }

    static net.minecraft.resources.Identifier coverTexture() {
        return View.COVER.register();
    }

    static net.minecraft.resources.Identifier depthTexture() {
        return View.DEPTH.register();
    }

    private static final class View extends net.minecraft.client.renderer.texture.AbstractTexture {
        private static final View UNDER = new View("translucency_under");
        private static final View COVER = new View("translucency_cover");
        private static final View DEPTH = new View("translucency_depth");
        private final net.minecraft.resources.Identifier id;

        private View(String path) {
            this.id = dev.golem.core.Ids.of("golemcore", path);
        }

        private net.minecraft.resources.Identifier register() {
            Minecraft.getInstance().getTextureManager().register(id, this);
            return id;
        }

        @Override
        public com.mojang.blaze3d.textures.GpuTextureView getTextureView() {
            if (this == DEPTH) {
                RenderTarget depth = WorldEffects.worldDepth();
                if (depth != null) return depth.getDepthTextureView();
            } else if (active) {
                return (this == UNDER ? under : cover).getColorTextureView();
            }
            return Minecraft.getInstance().getTextureManager().getTexture(HeatDistortionRenderType.sceneTexture())
                    .getTextureView();
        }

        @Override
        public com.mojang.blaze3d.textures.GpuSampler getSampler() {
            return RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST);
        }
    }

    /** Alpha := 0 (the sampled alpha is multiplied by zero). */
    private static final com.mojang.blaze3d.pipeline.RenderPipeline CLEAR_ALPHA = alphaPipeline("clear",
            new com.mojang.blaze3d.pipeline.BlendFunction(
                    com.mojang.blaze3d.platform.SourceFactor.ZERO, com.mojang.blaze3d.platform.DestFactor.ONE,
                    com.mojang.blaze3d.platform.SourceFactor.ZERO, com.mojang.blaze3d.platform.DestFactor.ZERO));
    /** Alpha := coverage + before·(1 - coverage), the saved alpha drawn under the target's own. */
    private static final com.mojang.blaze3d.pipeline.RenderPipeline RESTORE_ALPHA = alphaPipeline("restore",
            new com.mojang.blaze3d.pipeline.BlendFunction(
                    com.mojang.blaze3d.platform.SourceFactor.ZERO, com.mojang.blaze3d.platform.DestFactor.ONE,
                    com.mojang.blaze3d.platform.SourceFactor.ONE_MINUS_DST_ALPHA,
                    com.mojang.blaze3d.platform.DestFactor.ONE));

    private static com.mojang.blaze3d.pipeline.RenderPipeline alphaPipeline(String name,
            com.mojang.blaze3d.pipeline.BlendFunction blend) {
        return com.mojang.blaze3d.pipeline.RenderPipeline.builder()
                .withLocation(dev.golem.core.Ids.of("golemcore", "pipeline/translucency_" + name))
                .withVertexShader(dev.golem.core.Ids.of("golemcore", "core/translucency_alpha"))
                .withFragmentShader(dev.golem.core.Ids.of("golemcore", "core/translucency_alpha"))
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
                .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(java.util.Optional.of(blend),
                        com.mojang.blaze3d.pipeline.ColorTargetState.WRITE_ALPHA))
                .withCull(false).build();
    }

    private static void alphaPass(RenderTarget main, com.mojang.blaze3d.pipeline.RenderPipeline pipeline) {
        var builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        builder.addVertex(-1F, -1F, 0F);
        builder.addVertex(3F, -1F, 0F);
        builder.addVertex(-1F, 3F, 0F);
        try (var mesh = builder.buildOrThrow()) {
            var vertices = DefaultVertexFormat.POSITION.uploadImmediateVertexBuffer(mesh.vertexBuffer());
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST);
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Translucency alpha", main.getColorTextureView(), java.util.OptionalInt.empty())) {
                pass.setPipeline(pipeline);
                pass.bindTexture("Sampler0", under.getColorTextureView(), sampler);
                pass.setVertexBuffer(0, vertices);
                pass.draw(0, 3);
            }
        }
    }

    private static void copyColour(RenderTarget source, RenderTarget target) {
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source.getColorTexture(),
                target.getColorTexture(), 0, 0, 0, 0, 0, source.width, source.height);
    }
    //?} else {
    /*private static net.minecraft.client.renderer.ShaderInstance shader;

    static int underTextureId() {
        return active ? under.getColorTextureId() : HeatDistortionRenderType.sceneTextureId();
    }

    static int coverTextureId() {
        return active ? cover.getColorTextureId() : HeatDistortionRenderType.sceneTextureId();
    }

    static int depthTextureId() {
        RenderTarget depth = WorldEffects.worldDepth();
        return depth != null ? depth.getDepthTextureId() : HeatDistortionRenderType.sceneTextureId();
    }

    private static void copyColour(RenderTarget source, RenderTarget target) {
        int read = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        int activeTexture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        int texture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        try {
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,
                    source.frameBufferId);
            RenderSystem.bindTexture(target.getColorTextureId());
            com.mojang.blaze3d.platform.GlStateManager._glCopyTexSubImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, 0, 0,
                    0, 0, source.width, source.height);
        } finally {
            RenderSystem.bindTexture(texture);
            RenderSystem.activeTexture(activeTexture);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, read);
        }
    }

    private static void clearAlpha(RenderTarget main) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var colorMask = stack.malloc(4);
            var clearColor = stack.mallocFloat(4);
            org.lwjgl.opengl.GL11.glGetBooleanv(org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK, colorMask);
            org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            boolean scissor = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            try {
                main.bindWrite(false);
                com.mojang.blaze3d.platform.GlStateManager._disableScissorTest();
                RenderSystem.colorMask(false, false, false, true);
                RenderSystem.clearColor(0F, 0F, 0F, 0F);
                RenderSystem.clear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
            } finally {
                RenderSystem.clearColor(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3));
                RenderSystem.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
                if (scissor) com.mojang.blaze3d.platform.GlStateManager._enableScissorTest();
            }
        }
    }

    private static void restoreAlphaLegacy(RenderTarget main) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4);
            var colorMask = stack.malloc(4);
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);
            org.lwjgl.opengl.GL11.glGetBooleanv(org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK, colorMask);
            boolean depthTest = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            boolean depthWrite = org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
            boolean blend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
            int srcRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
            int dstRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
            int srcAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
            int dstAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
            boolean cull = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
            boolean scissor = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            int program = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
            int activeTexture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            int texture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            try {
                if (shader == null) shader = ShaderLoader.load(Minecraft.getInstance().getResourceManager(),
                        "golemcore:translucency_alpha", DefaultVertexFormat.POSITION);
                main.bindWrite(true);
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.colorMask(false, false, false, true);
                RenderSystem.disableCull();
                com.mojang.blaze3d.platform.GlStateManager._disableScissorTest();
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(org.lwjgl.opengl.GL11.GL_ZERO, org.lwjgl.opengl.GL11.GL_ONE,
                        org.lwjgl.opengl.GL11.GL_ONE_MINUS_DST_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
                // Bound directly, like DepthMerge: no vanilla uniforms, and ShaderInstance's caches stay untouched.
                com.mojang.blaze3d.shaders.ProgramManager.glUseProgram(shader.getId());
                RenderSystem.bindTexture(under.getColorTextureId());
                int location = com.mojang.blaze3d.shaders.Uniform.glGetUniformLocation(shader.getId(), "Sampler0");
                com.mojang.blaze3d.shaders.Uniform.uploadInteger(location, 0);
                //? if >=1.21 {
                var builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
                builder.addVertex(-1F, -1F, 0F);
                builder.addVertex(3F, -1F, 0F);
                builder.addVertex(-1F, 3F, 0F);
                com.mojang.blaze3d.vertex.BufferUploader.draw(builder.buildOrThrow());
                //?} else {
                /^var builder = Tesselator.getInstance().getBuilder();
                builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
                builder.vertex(-1F, -1F, 0F).endVertex();
                builder.vertex(3F, -1F, 0F).endVertex();
                builder.vertex(-1F, 3F, 0F).endVertex();
                com.mojang.blaze3d.vertex.BufferUploader.draw(builder.end());
                ^///?}
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not load translucency alpha shader", exception);
            } finally {
                com.mojang.blaze3d.platform.GlStateManager._glUseProgram(program);
                RenderSystem.bindTexture(texture);
                RenderSystem.activeTexture(activeTexture);
                RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
                if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
                RenderSystem.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                RenderSystem.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
                RenderSystem.depthMask(depthWrite);
                if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
                if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
                if (scissor) com.mojang.blaze3d.platform.GlStateManager._enableScissorTest();
                main.bindWrite(false);
            }
        }
    }
    *///?}
}
