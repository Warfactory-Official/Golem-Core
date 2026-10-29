package dev.golem.core.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
//? if >=26.1 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
//?} else {
/*import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
*///?}

/** Exact render state for the old Torex flare, and for a flare drawn inside {@code glDisable(GL_FOG)}. */
final class TorexFlareRenderType {

    private TorexFlareRenderType() {}

    //? if >=26.1 {
    private static final RenderPipeline PIPELINE = WorldPipelines.add(RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(dev.golem.core.Ids.parse("golemcore:pipeline/golem_torex_flare"))
            .withVertexShader(dev.golem.core.Ids.parse("golemcore:core/golem_torex_flare"))
            .withFragmentShader(dev.golem.core.Ids.parse("golemcore:core/golem_torex_flare"))
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withCull(false)
            .build());

    private static final Function<Identifier, RenderType> BY_TEXTURE = Util.memoize(texture ->
            RenderType.create("golem_torex_flare", RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .createRenderSetup()));

    static RenderType of(Identifier texture) {
        return BY_TEXTURE.apply(texture);
    }

    static RenderType fogless(Identifier texture) {
        return BY_TEXTURE.apply(texture);
    }
    //?} else {
    /*static RenderType of(ResourceLocation texture) {
        return RenderType.energySwirl(texture, 0F, 0F);
    }

    static RenderType fogless(ResourceLocation texture) {
        return FOGLESS.apply(texture);
    }

    private static final Function<ResourceLocation, RenderType> FOGLESS = Util.memoize(Factory::fogless);

    private static ShaderInstance shader;
    private static boolean reloadRegistered;

    // apply() resets the blend on 1.20.1 (the JSON states none), so the hook run after it restores SRC_ALPHA/ONE.
    private static ShaderInstance shader() {
        if (!reloadRegistered) {
            reloadRegistered = true;
            dev.golem.core.client.ClientResourceReloads.register("golemcore", "flare_legacy", () -> {
                if (shader != null) shader.close();
                shader = null;
            });
        }
        if (shader == null) {
            try {
                shader = ShaderLoader.load(net.minecraft.client.Minecraft.getInstance().getResourceManager(),
                        "golemcore:flare_legacy", DefaultVertexFormat.NEW_ENTITY, () -> {
                            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                            com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                                    com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
                        });
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not load shader golemcore:flare_legacy", e);
            }
        }
        return shader;
    }

    // create and the state shards are protected on RenderType, hence the subclass
    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        private static final ShaderStateShard SHADER = new ShaderStateShard(TorexFlareRenderType::shader);

        static RenderType fogless(ResourceLocation texture) {
            return RenderType.create("golem_flare", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true, CompositeState.builder()
                            .setShaderState(SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }
    }
    *///?}
}
