package dev.golem.core.render;

//? if >=1.21.11 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
*///?}

/** Textureless SRC_ALPHA/ONE_MINUS_SRC_ALPHA smoke ribbons, with depth writes disabled. */
public final class WeaponSmokeRenderType {
    private WeaponSmokeRenderType() {}

    //? if >=1.21.11 {
    private static final RenderPipeline PIPELINE = WorldPipelines.add(PipelineStates.depth(PipelineStates.blend(PipelineStates.vertices(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation("pipeline/golem_weapon_smoke")
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            , DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS), BlendFunction.TRANSLUCENT),
            PipelineStates.Depth.LESS_OR_EQUAL, false)
            .withCull(false)
            .build());

    private static final RenderType TYPE = RenderType.create("golem_weapon_smoke",
            RenderSetup.builder(PIPELINE).createRenderSetup());
    //?} else {
    /*private static final RenderType TYPE = Factory.smoke();

    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        private static RenderType smoke() {
            return create("golem_weapon_smoke", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                    256, false, true, CompositeState.builder()
                            .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }
    }
    *///?}

    public static RenderType get() {
        return TYPE;
    }
}
