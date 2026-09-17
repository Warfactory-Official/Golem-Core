package dev.golem.core.render;

//? if >=26.1 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * {@link SubmitSink} backed by vanilla's collector, for versions that have the submit pipeline
 * natively. Geometry passes straight through; the engine owns batching and draw ordering.
 *
 * <p>Two collector references are kept because vanilla needs both. Geometry goes to {@code target},
 * which may be an ordered view produced by {@link #order}. Items need the <i>root</i> collector:
 * {@code ItemStackRenderState#submit} accepts only the full {@code SubmitNodeCollector}, never an
 * ordered view. Item draws therefore ignore the current order on 26.1 — a vanilla limitation this
 * facade surfaces rather than invents.
 */
public final class VanillaSubmitSink implements SubmitSink {

    private final OrderedSubmitNodeCollector target;
    private final SubmitNodeCollector root;
    private final PassState passState;
    /** The camera of the pass, which only entity renderers receive; block-entity submission needs it. */
    private final @Nullable CameraRenderState camera;

    /** Scoped to one root renderer submission, never to vanilla's collector which is reused per frame. */
    private static final class PassState {
        private boolean sceneCaptured;
    }

    public VanillaSubmitSink(SubmitNodeCollector collector) {
        this(collector, collector, new PassState(), null);
    }

    public VanillaSubmitSink(SubmitNodeCollector collector, @Nullable CameraRenderState camera) {
        this(collector, collector, new PassState(), camera);
    }

    private VanillaSubmitSink(OrderedSubmitNodeCollector target, SubmitNodeCollector root, PassState passState,
                              @Nullable CameraRenderState camera) {
        this.target = target;
        this.root = root;
        this.passState = passState;
        this.camera = camera;
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometry geometry) {
        target.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            VertexConsumer drawBuffer = buffer;
            if (!passState.sceneCaptured && HeatDistortionRenderType.isSceneSampling(renderType)) {
                // The callback owns the currently-open buffer. Capture before writing to it, but never
                // end/reacquire that buffer from inside its own FeatureRenderDispatcher callback.
                HeatDistortionRenderType.prepareSceneForPass();
                passState.sceneCaptured = true;
            }
            geometry.render(pose, drawBuffer);
        });
    }

    @Override
    public void submitItem(PoseStack poseStack, CompatItemRenderState item, int light, int overlay, int seed) {
        item.delegate.submit(poseStack, root, light, overlay, seed);
    }

    @Override
    public void submitBlock(PoseStack poseStack, CompatBlockRenderState block, int light, int overlay) {
        if (block.isEmpty()) return;
        block.delegate.submit(poseStack, root, light, overlay, 0);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, CompatMovingBlock block) {
        if (block.isEmpty()) return;
        target.submitMovingBlock(poseStack, block.delegate);
    }

    @Override
    public void submitBlockEntity(PoseStack poseStack, CompatBlockEntityRenderState blockEntity) {
        if (blockEntity.isEmpty() || blockEntity.delegate == null || camera == null) return;
        Minecraft.getInstance().getBlockEntityRenderDispatcher().submit(blockEntity.delegate, poseStack, root, camera);
    }

    @Override
    public void submitText(PoseStack poseStack, Font font, float x, float y, FormattedCharSequence text,
                           boolean shadow, Font.DisplayMode mode, int light, int color, int backgroundColor) {
        target.submitText(poseStack, x, y, text, shadow, mode, light, color, backgroundColor, 0);
    }

    @Override
    public SubmitSink order(int order) {
        return new VanillaSubmitSink(root.order(order), root, passState, camera);
    }
}
//?}
