package dev.golem.core.render;

//? if >=26.1 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/**
 * Vanilla's line type at a fixed pixel width - 1.7's {@code glLineWidth(n)} before a {@code GL_LINES} draw.
 *
 * <p>26.1 carries the width on every line vertex, so there is one render type and the width goes through
 * {@link Vertexes#line(com.mojang.blaze3d.vertex.VertexConsumer, com.mojang.blaze3d.vertex.PoseStack.Pose, float, float,
 * float, int, float, float, float, float)}. Before 26.1 the width is render state ({@code LineStateShard}), so each
 * width is its own copy of {@code RenderType.lines()}; the vertex call there ignores the width argument.
 */
public final class LineRenderTypes {

    private LineRenderTypes() {}

    //? if >=26.1 {
    private static final com.mojang.blaze3d.pipeline.RenderPipeline SEE_THROUGH_PIPELINE =
            WorldPipelines.add(com.mojang.blaze3d.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.LINES_SNIPPET)
                    .withLocation("pipeline/golem_lines_see_through")
                    .withDepthStencilState(java.util.Optional.empty())
                    .build());
    private static final RenderType SEE_THROUGH = RenderType.create("golem_lines_see_through",
            net.minecraft.client.renderer.rendertype.RenderSetup.builder(SEE_THROUGH_PIPELINE).createRenderSetup());

    public static RenderType lines(float width) {
        return RenderTypes.lines();
    }

    /** Lines drawn over everything: no depth test, no depth write (1.7 {@code glDisable(GL_DEPTH_TEST)}). */
    public static RenderType seeThrough(float width) {
        return SEE_THROUGH;
    }
    //?} else {
    /*private static final java.util.Map<Float, RenderType> BY_WIDTH = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<Float, RenderType> SEE_THROUGH = new java.util.concurrent.ConcurrentHashMap<>();

    public static RenderType lines(float width) {
        return BY_WIDTH.computeIfAbsent(width, w -> Factory.lines(w, false));
    }

    /^* Lines drawn over everything: no depth test, no depth write (1.7 {@code glDisable(GL_DEPTH_TEST)}). ^/
    public static RenderType seeThrough(float width) {
        return SEE_THROUGH.computeIfAbsent(width, w -> Factory.lines(w, true));
    }

    /^* {@code create} and the state shards are protected on {@code RenderType}, hence the subclass. ^/
    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        static RenderType lines(float width, boolean seeThrough) {
            CompositeState.CompositeStateBuilder state = CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(java.util.OptionalDouble.of(width)))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL);
            if (seeThrough) {
                state.setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE);
            } else {
                state.setLayeringState(VIEW_OFFSET_Z_LAYERING).setOutputState(ITEM_ENTITY_TARGET)
                        .setWriteMaskState(COLOR_DEPTH_WRITE);
            }
            return RenderType.create((seeThrough ? "golem_lines_see_through_" : "golem_lines_") + width,
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES, 256, false, false,
                    state.createCompositeState(false));
        }
    }
    *///?}
}
