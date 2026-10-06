package dev.golem.core.render;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
//?} else {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
*///?}

/**
 * The particle layers of {@link CompatParticle#keepFaintFragments()}: blended over the particle atlas, depth tested but
 * never depth written, and drawing <b>every fragment whose alpha is above zero</b> - a 1.7 particle that called
 * {@code glAlphaFunc(GL_GREATER, 0)} (or disabled {@code GL_ALPHA_TEST}) and {@code glDepthMask(false)} before it drew.
 *
 * <p>Vanilla's particle fragment shader discards {@code texture * vertex colour} below alpha 0.1 on every node, so a
 * particle whose whole alpha is below that (1.7's white-phosphorus haze peaks at 0.025) is not drawn at all. The only
 * difference here is that test: {@code golemcore:particle_faint} is vanilla's particle program with the discard moved to
 * alpha 0, over vanilla's own particle vertex shader. Without the depth write a faint quad cannot hide what is drawn
 * after it, which is why the two go together.
 */
final class FaintParticleLayers {

    private FaintParticleLayers() {}

    //? if >=26.1 {
    private static final RenderPipeline TRANSLUCENT_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation("pipeline/golem_particle_faint")
            .withFragmentShader(dev.golem.core.Ids.parse("golemcore:core/particle_faint"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .build();
    private static final RenderPipeline ADDITIVE_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation("pipeline/golem_particle_faint_additive")
            .withFragmentShader(dev.golem.core.Ids.parse("golemcore:core/particle_faint"))
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .build();

    static final SingleQuadParticle.Layer TRANSLUCENT =
            new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, TRANSLUCENT_PIPELINE);
    static final SingleQuadParticle.Layer ADDITIVE =
            new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, ADDITIVE_PIPELINE);
    //?} else {
    /*private static final String SHADER = "golemcore:particle_faint";
    private static ShaderInstance translucentShader;
    private static ShaderInstance additiveShader;
    private static boolean reloadRegistered;

    // One program per blend: the program's apply() resets the blend (on 1.20.1 from its JSON, which states none on
    // purpose), and the hook run after it sets this layer's own again. See NoiseDisplacedRenderType for the 1.20.1 trap.
    private static ShaderInstance shader(boolean additive) {
        if (!reloadRegistered) {
            reloadRegistered = true;
            dev.golem.core.client.ClientResourceReloads.register("golemcore", "particle_faint", () -> {
                if (translucentShader != null) translucentShader.close();
                if (additiveShader != null) additiveShader.close();
                translucentShader = null;
                additiveShader = null;
            });
        }
        ShaderInstance shader = additive ? additiveShader : translucentShader;
        if (shader != null) return shader;
        try {
            shader = ShaderLoader.load(Minecraft.getInstance().getResourceManager(), SHADER, DefaultVertexFormat.PARTICLE,
                    () -> blend(additive));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not load shader " + SHADER, e);
        }
        if (additive) additiveShader = shader;
        else translucentShader = shader;
        return shader;
    }

    private static void blend(boolean additive) {
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }
    }

    static final ParticleRenderType TRANSLUCENT = layer(false);
    static final ParticleRenderType ADDITIVE = layer(true);

    private static ParticleRenderType layer(boolean additive) {
        return new ParticleRenderType() {
            //? if >=1.21 {
            @Override
            public com.mojang.blaze3d.vertex.BufferBuilder begin(com.mojang.blaze3d.vertex.Tesselator tesselator,
                    net.minecraft.client.renderer.texture.TextureManager textureManager) {
                setup();
                return tesselator.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            }
            //?} else {
            /^@Override
            public void begin(com.mojang.blaze3d.vertex.BufferBuilder buffer,
                    net.minecraft.client.renderer.texture.TextureManager textureManager) {
                setup();
                buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            }

            @Override
            public void end(com.mojang.blaze3d.vertex.Tesselator tesselator) {
                tesselator.end();
                RenderSystem.defaultBlendFunc();
                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
            }
            ^///?}

            private void setup() {
                ShaderInstance shader = shader(additive);
                RenderSystem.setShader(() -> shader);
                RenderSystem.depthMask(false);
                RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
                blend(additive);
            }

            @Override
            public String toString() {
                return additive ? "GOLEM_PARTICLE_FAINT_ADDITIVE" : "GOLEM_PARTICLE_FAINT";
            }
        };
    }
    *///?}
}
