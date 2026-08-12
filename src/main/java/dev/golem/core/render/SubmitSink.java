package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

//? if >=26.1 {
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/**
 * Where a renderer sends its geometry.
 *
 * <p>This mirrors 26.1's {@code SubmitNodeCollector}: you hand it a pose, a {@link RenderType} and a
 * callback that writes vertices. The callback signature — {@code (PoseStack.Pose, VertexConsumer)} —
 * is the same shape older versions used for immediate-mode drawing, which is what makes this a
 * backport rather than a reimplementation.
 *
 * <p>On 26.1 the implementation delegates to the vanilla collector, and the engine draws the geometry
 * later. On older versions {@code RecordingSubmitSink} records submissions and draws them into a
 * {@code MultiBufferSource} at the end of the pass.
 */
public interface SubmitSink {

    /** Submit geometry to be drawn with the given render type at the given pose. */
    void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometry geometry);

    /**
     * Returns a sink whose geometry draws after everything submitted at a lower order. Order 0 is the
     * default. Mirrors {@code SubmitNodeCollector#order}.
     */
    SubmitSink order(int order);

    /**
     * Draws an item stack — a machine's output preview sitting on its model, say.
     *
     * <p>This lives on the sink rather than being done by the caller because the two eras disagree
     * about who owns item rendering: 26.1 submits a pre-baked render state, older versions call
     * {@code ItemRenderer} at draw time. {@link CompatItemRenderState} holds whichever the target
     * needs.
     *
     */
    void submitItem(PoseStack poseStack, CompatItemRenderState item, int light, int overlay, int seed);

    /** Writes the vertices for one submitted piece of geometry. */
    @FunctionalInterface
    interface CustomGeometry {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }
}
