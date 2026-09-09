package dev.golem.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

/** Combines split world targets into an independent, nearest-surface depth snapshot. */
final class DepthMerge {
    private static boolean reported;
    private DepthMerge() {}

    //? if >=26.1 {
    private static final com.mojang.blaze3d.pipeline.RenderPipeline PIPELINE =
            com.mojang.blaze3d.pipeline.RenderPipeline.builder()
                    .withLocation(dev.golem.core.Ids.of("golemcore", "pipeline/depth_merge"))
                    .withVertexShader(dev.golem.core.Ids.of("golemcore", "core/depth_merge"))
                    .withFragmentShader(dev.golem.core.Ids.of("golemcore", "core/depth_merge"))
                    .withSampler("Sampler0").withSampler("Sampler1").withSampler("Sampler2")
                    .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
                    .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
                            com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS, true))
                    .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(
                            java.util.Optional.empty(), com.mojang.blaze3d.pipeline.ColorTargetState.WRITE_NONE))
                    .withCull(false).build();
    //?} else {
    /*private static net.minecraft.client.renderer.ShaderInstance shader;
    *///?}

    static void close() {
        reported = false;
        //? if <26.1 {
        /*if (shader != null) shader.close();
        shader = null;
        *///?}
    }

    static void merge(RenderTarget output, RenderTarget main, RenderTarget terrain, RenderTarget entities) {
        if (!output.useDepth) throw new IllegalArgumentException("Depth merge output requires a depth attachment");
        if (terrain == null) terrain = main;
        if (entities == null) entities = main;
        if (output == main || output == terrain || output == entities)
            throw new IllegalArgumentException("Depth merge cannot sample its output attachment");
        for (RenderTarget input : new RenderTarget[] {main, terrain, entities}) {
            if (!input.useDepth || input.width != output.width || input.height != output.height)
                throw new IllegalArgumentException("Depth merge requires matching depth-capable targets");
        }
        if (terrain == main && entities == main) {
            output.copyDepthFrom(main);
            return;
        }
        //? if >=26.1 {
        var builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        builder.addVertex(-1F, -1F, 0F);
        builder.addVertex(3F, -1F, 0F);
        builder.addVertex(-1F, 3F, 0F);
        try (var mesh = builder.buildOrThrow()) {
            var vertices = DefaultVertexFormat.POSITION.uploadImmediateVertexBuffer(mesh.vertexBuffer());
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST);
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "World effect nearest depth", output.getColorTextureView(), java.util.OptionalInt.empty(),
                    output.getDepthTextureView(), java.util.OptionalDouble.empty())) {
                pass.setPipeline(PIPELINE);
                pass.bindTexture("Sampler0", main.getDepthTextureView(), sampler);
                pass.bindTexture("Sampler1", terrain.getDepthTextureView(), sampler);
                pass.bindTexture("Sampler2", entities.getDepthTextureView(), sampler);
                pass.setVertexBuffer(0, vertices);
                pass.draw(0, 3);
            }
        }
        //?} else {
        /*mergeLegacy(output, main, terrain, entities);
        *///?}
        if (!reported) {
            reported = true;
            dev.golem.core.GolemCore.LOG.info("World depth merge active: main + terrain + entities ({}x{})", output.width, output.height);
        }
    }

    //? if <26.1 {
    /*private static void mergeLegacy(RenderTarget output, RenderTarget main, RenderTarget terrain, RenderTarget entities) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4);
            var colorMask = stack.malloc(4);
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);
            org.lwjgl.opengl.GL11.glGetBooleanv(org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK, colorMask);
            boolean depthTest = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            boolean depthWrite = org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
            boolean cull = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
            boolean scissor = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            int depthFunc = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC);
            int program = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
            int activeTexture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
            int[] textures = new int[3];
            for (int i = 0; i < textures.length; i++) {
                RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + i);
                textures[i] = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            }
            RenderSystem.activeTexture(activeTexture);
            try {
                if (shader == null) shader = ShaderLoader.load(net.minecraft.client.Minecraft.getInstance().getResourceManager(),
                        "golemcore:depth_merge", DefaultVertexFormat.POSITION);
                output.bindWrite(true);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_ALWAYS);
                RenderSystem.depthMask(true);
                RenderSystem.colorMask(false, false, false, false);
                RenderSystem.disableCull();
                RenderSystem.disableScissor();
                // This depth-only program has no vanilla matrix/color uniforms. Bind it directly through
                // the public shader API and restore the old program below, leaving ShaderInstance's
                // program/blend caches and every texture unit above our three inputs untouched.
                com.mojang.blaze3d.shaders.ProgramManager.glUseProgram(shader.getId());
                int[] depths = {main.getDepthTextureId(), terrain.getDepthTextureId(), entities.getDepthTextureId()};
                for (int i = 0; i < depths.length; i++) {
                    RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + i);
                    RenderSystem.bindTexture(depths[i]);
                    int location = com.mojang.blaze3d.shaders.Uniform.glGetUniformLocation(shader.getId(), "Sampler" + i);
                    com.mojang.blaze3d.shaders.Uniform.uploadInteger(location, i);
                }
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
                throw new IllegalStateException("Could not load world depth merge shader", exception);
            } finally {
                com.mojang.blaze3d.platform.GlStateManager._glUseProgram(program);
                for (int i = 0; i < textures.length; i++) {
                    RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + i);
                    RenderSystem.bindTexture(textures[i]);
                }
                RenderSystem.activeTexture(activeTexture);
                RenderSystem.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                RenderSystem.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
                RenderSystem.depthMask(depthWrite);
                RenderSystem.depthFunc(depthFunc);
                if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
                if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
                if (scissor) com.mojang.blaze3d.platform.GlStateManager._enableScissorTest();
            }
        }
    }
    *///?}
}
