package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

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

    /**
     * Draws a block state as a free-standing model at the pose — a disguise, a display block. The
     * block is lit by {@code light}, not by the world, exactly like an item.
     */
    void submitBlock(PoseStack poseStack, CompatBlockRenderState block, int light, int overlay);

    /**
     * Draws a block in flight with the world lighting of the position it was extracted at, the way
     * vanilla draws falling sand. The block model's origin is the pose; callers translate by
     * {@code (-0.5, 0, -0.5)} to centre it on an entity.
     */
    void submitMovingBlock(PoseStack poseStack, CompatMovingBlock block);

    /**
     * Draws a block entity that is not in the world through its own registered renderer. Nothing is
     * drawn for a block entity without one.
     */
    void submitBlockEntity(PoseStack poseStack, CompatBlockEntityRenderState blockEntity);

    /** Submits text with the same deferred-pose and ordering rules as geometry. */
    void submitText(PoseStack poseStack, Font font, float x, float y, FormattedCharSequence text,
                    boolean shadow, Font.DisplayMode mode, int light, int color, int backgroundColor);

    /** Writes the vertices for one submitted piece of geometry. */
    @FunctionalInterface
    interface CustomGeometry {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }
}
