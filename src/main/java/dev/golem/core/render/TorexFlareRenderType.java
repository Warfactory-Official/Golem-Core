package dev.golem.core.render;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
*///?}

/** Exact render state for the old Torex flare. */
final class TorexFlareRenderType {

    private TorexFlareRenderType() {}

    //? if >=26.1 {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(
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
            .build();

    private static final Function<Identifier, RenderType> BY_TEXTURE = Util.memoize(texture ->
            RenderType.create("golem_torex_flare", RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .createRenderSetup()));

    static RenderType of(Identifier texture) {
        return BY_TEXTURE.apply(texture);
    }
    //?} else {
    /*static RenderType of(ResourceLocation texture) {
        return RenderType.energySwirl(texture, 0F, 0F);
    }
    *///?}
}
