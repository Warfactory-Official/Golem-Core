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
//?} else {
/*import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
*///?}

/**
 * The untextured additive pass old renderers drew with {@code glDisable(GL_TEXTURE_2D)},
 * {@code glBlendFunc(GL_SRC_ALPHA, GL_ONE)}, {@code glDepthMask(false)} and {@code glDisable(GL_CULL_FACE)}:
 * POSITION_COLOR, depth-tested, no depth write, <b>no face culling</b>, the same on every node.
 *
 * <p>Vanilla's lightning and dragon-ray types both cull, and lightning also writes depth. Old geometry emits each
 * face once, with no consistent winding, so a culled type drops faces by view direction and the effect fades out
 * as the camera walks around it; a depth-writing one lets near faces hide the far faces of the same glow.
 */
final class AdditiveRenderType {

    private AdditiveRenderType() {}

    //? if >=26.1 {
    private static final RenderType QUADS = create("golem_additive", VertexFormat.Mode.QUADS, false);
    private static final RenderType TRIANGLES = create("golem_additive_triangles", VertexFormat.Mode.TRIANGLES, false);
    private static final RenderType QUADS_DEPTH = create("golem_additive_depth", VertexFormat.Mode.QUADS, true);

    private static RenderType create(String name, VertexFormat.Mode mode, boolean depthWrite) {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                .withLocation("pipeline/" + name)
                .withVertexShader("core/rendertype_lightning")
                .withFragmentShader("core/rendertype_lightning")
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, mode)
                .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), depthWrite))
                .withCull(false)
                .build();
        return RenderType.create(name, RenderSetup.builder(pipeline).createRenderSetup());
    }
    //?} else {
    /*private static final RenderType QUADS = Factory.additive("golem_additive", VertexFormat.Mode.QUADS, false);
    private static final RenderType TRIANGLES = Factory.additive("golem_additive_triangles", VertexFormat.Mode.TRIANGLES, false);
    private static final RenderType QUADS_DEPTH = Factory.additive("golem_additive_depth", VertexFormat.Mode.QUADS, true);
    private static final RenderType DRAGON_RAYS = Factory.legacyDragonRays();

    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        private static RenderType additive(String name, VertexFormat.Mode mode, boolean depthWrite) {
            return create(name, DefaultVertexFormat.POSITION_COLOR, mode, 1536, false, false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(depthWrite ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                            .createCompositeState(false));
        }

        /^ 26.1's DRAGON_RAYS: triangles, additive, culled, no depth write. Legacy lightning is quads and writes depth. ^/
        private static RenderType legacyDragonRays() {
            return create("golem_dragon_rays", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 1536,
                    false, false, CompositeState.builder()
                            .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }
    }

    static RenderType dragonRays() {
        return DRAGON_RAYS;
    }
    *///?}

    static RenderType quads() {
        return QUADS;
    }

    static RenderType triangles() {
        return TRIANGLES;
    }

    static RenderType quadsDepth() {
        return QUADS_DEPTH;
    }
}
