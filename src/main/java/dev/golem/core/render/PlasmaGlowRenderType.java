package dev.golem.core.render;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;
//?} else {
/*import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;

import java.util.function.Function;
*///?}

/**
 * The render type for a self-lit glow drawn over solid geometry (plasma, beams, flames), with the state a 1.7
 * renderer sets by hand:
 *
 * <ul>
 *   <li><b>{@code blendFunc(SRC_ALPHA, ONE)}</b>: alpha-weighted additive. Plain additive ({@code ONE/ONE}) would
 *       ignore the fragment alpha, so a pulsing effect would render at constant full brightness.</li>
 *   <li><b>{@code depthMask(false)}</b>: depth is tested but not written, so coincident layers do not z-fight.</li>
 *   <li><b>culling off</b>: a hollow shell is seen from the inside too.</li>
 *   <li><b>emissive / full-bright</b>: the effect lights itself.</li>
 * </ul>
 *
 * <p>No stock render type has that combination: 26.1's {@code energySwirl} is additive but writes depth, and
 * {@code eyes} neither weights by alpha nor leaves culling off. The pipeline is declared here and memoized per
 * texture.
 *
 * <p>{@link #ofSeeThrough} is the same glow with the depth test off as well, for a 1.12 flare that called
 * {@code disableDepth()} and so showed through everything.
 */
public final class PlasmaGlowRenderType {

    private PlasmaGlowRenderType() {}

    /** Register during client initialization, before Forge dispatches its reload-registration event. */
    public static void init() {
        //? if <1.21 {
        /*Factory.registerReloads();
        *///?}
    }

    //? if >=26.1 {
    private static final RenderPipeline PIPELINE = WorldPipelines.add(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation("pipeline/golem_plasma_glow")
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            // Depth test as normal, depth WRITE off.
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build());

    private static final Function<Identifier, RenderType> BY_TEXTURE = Util.memoize(texture ->
            RenderType.create("golem_plasma_glow",
                    RenderSetup.builder(PIPELINE).withTexture("Sampler0", texture).createRenderSetup()));

    private static final RenderPipeline SEE_THROUGH_PIPELINE = WorldPipelines.add(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation("pipeline/golem_plasma_glow_see_through")
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withDepthStencilState(java.util.Optional.empty())
            .withCull(false)
            .build());

    private static final Function<Identifier, RenderType> SEE_THROUGH = Util.memoize(texture ->
            RenderType.create("golem_plasma_glow_see_through",
                    RenderSetup.builder(SEE_THROUGH_PIPELINE).withTexture("Sampler0", texture).createRenderSetup()));

    public static RenderType of(Identifier texture) {
        return BY_TEXTURE.apply(texture);
    }

    public static RenderType ofSeeThrough(Identifier texture) {
        return SEE_THROUGH.apply(texture);
    }
    //?} else {
    /*/^*
     * Pre-26.1 there is no pipeline object; the same state is a {@code CompositeState}. {@code create}
     * is protected on {@code RenderType}, hence the subclass.
     ^/
    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        //? if <1.21 {
        /^private static net.minecraft.client.renderer.ShaderInstance shader;
        private static boolean reloadRegistered;

        private static void registerReloads() {
            if (!reloadRegistered) {
                reloadRegistered = true;
                dev.golem.core.client.ClientResourceReloads.register("golemcore", "plasma_glow", () -> {
                    if (shader != null) shader.close();
                    shader = null;
                });
            }
        }

        private static net.minecraft.client.renderer.ShaderInstance shader() {
            if (shader == null) {
                // A named class, not an anonymous one: javac derives an anonymous constructor's parameters
                // from the superclass's, and some generated Forge 1.20.1 jars name them all alike
                // ("variable o is already defined").
                final class GlowShader extends net.minecraft.client.renderer.ShaderInstance {
                    GlowShader() throws java.io.IOException {
                        super(net.minecraft.client.Minecraft.getInstance().getResourceManager(),
                                "rendertype_entity_translucent_emissive", DefaultVertexFormat.NEW_ENTITY);
                    }

                    @Override
                    public void apply() {
                        super.apply();
                        // Keep vanilla's BlendMode cache in its normal state. A different JSON blend
                        // makes later shaders overwrite special ambient blends (notably the vignette).
                        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                        com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
                    }
                }
                try {
                    shader = new GlowShader();
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("Could not load additive glow shader", exception);
                }
            }
            return shader;
        }
        ^///?}

        static RenderType glow(ResourceLocation texture) {
            return glow(texture, false);
        }

        static RenderType glow(ResourceLocation texture, boolean seeThrough) {
            return create(seeThrough ? "golem_plasma_glow_see_through" : "golem_plasma_glow",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
                    CompositeState.builder()
                            //? if <1.21 {
                            /^.setShaderState(new ShaderStateShard(Factory::shader))
                            ^///?} else {
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            //?}
                            .setTextureState(new TextureStateShard(texture, false, false))
                            // SRC_ALPHA / ONE - the alpha-weighted additive blend, not ONE/ONE.
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setDepthTestState(seeThrough ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(false));
        }
    }

    private static final Function<ResourceLocation, RenderType> BY_TEXTURE = Util.memoize(texture -> Factory.glow(texture));
    private static final Function<ResourceLocation, RenderType> SEE_THROUGH =
            Util.memoize(texture -> Factory.glow(texture, true));

    public static RenderType of(ResourceLocation texture) {
        return BY_TEXTURE.apply(texture);
    }

    public static RenderType ofSeeThrough(ResourceLocation texture) {
        return SEE_THROUGH.apply(texture);
    }
    *///?}
}
