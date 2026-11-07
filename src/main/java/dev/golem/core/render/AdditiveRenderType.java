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
    private static final RenderType QUADS = create("golem_additive", VertexFormat.Mode.QUADS, false, true);
    private static final RenderType TRIANGLES = create("golem_additive_triangles", VertexFormat.Mode.TRIANGLES, false, true);
    private static final RenderType QUADS_DEPTH = create("golem_additive_depth", VertexFormat.Mode.QUADS, true, true);
    private static final RenderType TRIANGLES_FOGLESS =
            create("golem_additive_triangles_fogless", VertexFormat.Mode.TRIANGLES, false, false);

    // position_color is vanilla's untextured program without fog.
    private static RenderType create(String name, VertexFormat.Mode mode, boolean depthWrite, boolean fog) {
        String shader = fog ? "core/rendertype_lightning" : "core/position_color";
        RenderPipeline pipeline = WorldPipelines.add(RenderPipeline.builder(
                        fog ? RenderPipelines.MATRICES_FOG_SNIPPET : RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation("pipeline/" + name)
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, mode)
                .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), depthWrite))
                .withCull(false)
                .build());
        return RenderType.create(name, RenderSetup.builder(pipeline).createRenderSetup());
    }
    //?} else {
    /*private static final RenderType QUADS = Factory.additive("golem_additive", VertexFormat.Mode.QUADS, false, true);
    private static final RenderType TRIANGLES = Factory.additive("golem_additive_triangles", VertexFormat.Mode.TRIANGLES, false, true);
    private static final RenderType QUADS_DEPTH = Factory.additive("golem_additive_depth", VertexFormat.Mode.QUADS, true, true);
    private static final RenderType TRIANGLES_FOGLESS =
            Factory.additive("golem_additive_triangles_fogless", VertexFormat.Mode.TRIANGLES, false, false);
    private static final RenderType DRAGON_RAYS = Factory.legacyDragonRays();

    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        private static final java.util.Map<String, net.minecraft.client.renderer.ShaderInstance> SHADERS =
                new java.util.HashMap<>();
        private static boolean reloadRegistered;

        // A 1.20.1 program JSON's blend wins over the render type's in apply(); this one restores SRC_ALPHA/ONE after it.
        private static net.minecraft.client.renderer.ShaderInstance additiveShader(String name) {
            if (!reloadRegistered) {
                reloadRegistered = true;
                dev.golem.core.client.ClientResourceReloads.register("golemcore", "additive_lightning", () -> {
                    SHADERS.values().forEach(net.minecraft.client.renderer.ShaderInstance::close);
                    SHADERS.clear();
                });
            }
            net.minecraft.client.renderer.ShaderInstance loaded = SHADERS.get(name);
            if (loaded != null) return loaded;
            final class AdditiveShader extends net.minecraft.client.renderer.ShaderInstance {
                AdditiveShader() throws java.io.IOException {
                    super(net.minecraft.client.Minecraft.getInstance().getResourceManager(), name,
                            DefaultVertexFormat.POSITION_COLOR);
                }

                @Override
                public void apply() {
                    super.apply();
                    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                    com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                            com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                            com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
                }
            }
            try {
                loaded = new AdditiveShader();
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not load the additive shader " + name, exception);
            }
            SHADERS.put(name, loaded);
            return loaded;
        }

        //? if <1.21 {
        /^private static final ShaderStateShard ADDITIVE_SHADER =
                new ShaderStateShard(() -> additiveShader("rendertype_lightning"));
        ^///?} else {
        private static final ShaderStateShard ADDITIVE_SHADER = RENDERTYPE_LIGHTNING_SHADER;
        //?}
        private static final ShaderStateShard FOGLESS_SHADER = new ShaderStateShard(() -> additiveShader("position_color"));

        private static RenderType additive(String name, VertexFormat.Mode mode, boolean depthWrite, boolean fog) {
            return create(name, DefaultVertexFormat.POSITION_COLOR, mode, 1536, false, false,
                    CompositeState.builder()
                            .setShaderState(fog ? ADDITIVE_SHADER : FOGLESS_SHADER)
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(depthWrite ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                            .createCompositeState(false));
        }

        /^ 26.1's DRAGON_RAYS: triangles, additive, culled, no depth write. Legacy lightning is quads and writes depth. ^/
        private static RenderType legacyDragonRays() {
            return create("golem_dragon_rays", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 1536,
                    false, false, CompositeState.builder()
                            .setShaderState(ADDITIVE_SHADER)
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

    static RenderType trianglesFogless() {
        return TRIANGLES_FOGLESS;
    }
}
