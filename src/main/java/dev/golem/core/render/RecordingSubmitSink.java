package dev.golem.core.render;

//? if <26.1 {
/*import com.mojang.blaze3d.vertex.PoseStack;
 import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/^*
 * {@link SubmitSink} for versions without the submit pipeline: records submissions during the pass and
 * draws them into a {@code MultiBufferSource} at the end.
 *
 * <p>Drawing eagerly would be simpler, but it would silently discard {@link SubmitSink#order}: before
 * 26.1 geometry reaches the buffer source in call order, with no way to say "draw this after that".
 * Recording and replaying sorted by order reproduces 26.1's observable result. The sort is stable, so
 * submissions sharing an order keep their relative sequence — which is what an eager implementation
 * would have given, making order 0 alone behave exactly like immediate drawing.
 *
 * <p>The pose is <b>copied</b> at submit time, not referenced. {@link PoseStack#last()} returns the live
 * top {@code Pose} and the transform methods mutate it in place, so holding the reference would draw
 * every entry with the stack's final state — and callers routinely keep transforming, and pop, before
 * the flush happens.
 ^/
public final class RecordingSubmitSink implements SubmitSink {

    private sealed interface Entry permits GeometryEntry, ItemEntry, TextEntry, BlockEntry, MovingBlockEntry,
            BlockEntityEntry { int order(); }
    private record GeometryEntry(int order, Matrix4f pose, Matrix3f normal, RenderType renderType,
                                 CustomGeometry geometry) implements Entry {}
    private record ItemEntry(int order, PoseStack poseStack, CompatItemRenderState item, int light,
                             int overlay) implements Entry {}
    private record BlockEntry(int order, PoseStack poseStack, net.minecraft.world.level.block.state.BlockState state,
                              int light, int overlay) implements Entry {}
    private record MovingBlockEntry(int order, PoseStack poseStack, net.minecraft.world.level.block.state.BlockState state,
                                    net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                    net.minecraft.core.BlockPos seedPos) implements Entry {}
    private record BlockEntityEntry(int order, PoseStack poseStack,
                                    net.minecraft.world.level.block.entity.BlockEntity blockEntity,
                                    float partialTicks) implements Entry {}
    private record TextEntry(int order, PoseStack poseStack, Font font, float x, float y, FormattedCharSequence text,
                             boolean shadow, Font.DisplayMode mode, int light, int color, int background) implements Entry {}

    // One stable stream is essential: 26.1 orders geometry, items, and text together, not per kind.
    private final List<Entry> entries = new ArrayList<>();
    private final int order;

    public RecordingSubmitSink() {
        this(0);
    }

    private RecordingSubmitSink(int order) {
        this.order = order;
    }

    /^* A view tagging submissions with a different order, sharing this sink's entry list. ^/
    private RecordingSubmitSink(int order, RecordingSubmitSink parent) {
        this.order = order;
        // Deliberately aliases the parent's list rather than copying, so one flush drains everything
        // submitted through any view.
        this.sharedWith = parent;
    }

    private RecordingSubmitSink sharedWith;
    private boolean sceneCaptured;

    private List<Entry> sink() {
        return sharedWith != null ? sharedWith.sink() : entries;
    }

    private RecordingSubmitSink rootSink() {
        return sharedWith == null ? this : sharedWith.rootSink();
    }

    private static void flushPending(MultiBufferSource source) {
        if (source instanceof MultiBufferSource.BufferSource buffers) buffers.endBatch();
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometry geometry) {
        // copy(), not last(). PoseStack.last() hands back the LIVE top Pose, and translate/mulPose/scale
        // mutate it in place — so keeping the reference means every entry recorded at the same stack
        // level is drawn with whatever that pose held when the batch was finally flushed, not with the
        // transform it was submitted under. A renderer that accumulates joint transforms between
        // submissions (the assembly machine's arms) therefore drew its parts on top of each other.
        PoseStack.Pose pose = poseStack.last();
        sink().add(new GeometryEntry(order, new Matrix4f(pose.pose()), new Matrix3f(pose.normal()), renderType, geometry));
    }

    @Override
    public void submitItem(PoseStack poseStack, CompatItemRenderState item, int light, int overlay, int seed) {
        if (item.isEmpty()) return;
        // The PoseStack is copied, not snapshotted to a Pose: ItemRenderer needs a full stack to push
        // and pop around its own transforms, and the caller is free to pop before the flush.
        PoseStack copy = new PoseStack();
        copy.last().pose().set(poseStack.last().pose());
        copy.last().normal().set(poseStack.last().normal());
        sink().add(new ItemEntry(order, copy, item, light, overlay));
    }

    private static PoseStack copyOf(PoseStack poseStack) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(poseStack.last().pose());
        copy.last().normal().set(poseStack.last().normal());
        return copy;
    }

    @Override
    public void submitBlock(PoseStack poseStack, CompatBlockRenderState block, int light, int overlay) {
        if (block.isEmpty() || block.state == null) return;
        sink().add(new BlockEntry(order, copyOf(poseStack), block.state, light, overlay));
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, CompatMovingBlock block) {
        if (block.isEmpty() || block.blockState() == null || block.level == null || block.pos == null) return;
        sink().add(new MovingBlockEntry(order, copyOf(poseStack), block.blockState(), block.level, block.pos,
                block.seedPos == null ? block.pos : block.seedPos));
    }

    @Override
    public void submitBlockEntity(PoseStack poseStack, CompatBlockEntityRenderState blockEntity) {
        if (blockEntity.isEmpty() || blockEntity.blockEntity == null) return;
        sink().add(new BlockEntityEntry(order, copyOf(poseStack), blockEntity.blockEntity, blockEntity.partialTicks));
    }

    @Override
    public void submitText(PoseStack poseStack, Font font, float x, float y, FormattedCharSequence text,
                           boolean shadow, Font.DisplayMode mode, int light, int color, int background) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(poseStack.last().pose());
        copy.last().normal().set(poseStack.last().normal());
        sink().add(new TextEntry(order, copy, font, x, y, text, shadow, mode, light, color, background));
    }

    @Override
    public SubmitSink order(int newOrder) {
        return new RecordingSubmitSink(newOrder, this);
    }

    /^* Draws everything recorded, lowest order first, then clears. ^/
    public void flush(MultiBufferSource bufferSource, net.minecraft.world.level.@org.jspecify.annotations.Nullable Level level) {
        List<Entry> all = sink();
        RecordingSubmitSink root = rootSink();
        try {
            all.sort(Comparator.comparingInt(Entry::order));
            for (Entry entry : all) {
                if (entry instanceof GeometryEntry e) {
                    VertexConsumer buffer;
                    if (!root.sceneCaptured && HeatDistortionRenderType.isSceneSampling(e.renderType())) {
                        // Flush all pending buffers before the copy. Otherwise the framebuffer still lacks
                        // geometry queued by preceding entries and the shader samples an intermediate scene.
                        flushPending(bufferSource);
                        HeatDistortionRenderType.prepareSceneForPass();
                        root.sceneCaptured = true;
                        buffer = bufferSource.getBuffer(e.renderType());
                    } else {
                        buffer = bufferSource.getBuffer(e.renderType());
                    }
                    PoseStack poseStack = new PoseStack();
                    poseStack.last().pose().set(e.pose());
                    poseStack.last().normal().set(e.normal());
                    e.geometry().render(poseStack.last(), buffer);
                } else if (entry instanceof ItemEntry e) {
                    e.item().draw(e.poseStack(), bufferSource, e.light(), e.overlay(), level);
                } else if (entry instanceof BlockEntry e) {
                    net.minecraft.client.Minecraft.getInstance().getBlockRenderer()
                            .renderSingleBlock(e.state(), e.poseStack(), bufferSource, e.light(), e.overlay());
                } else if (entry instanceof MovingBlockEntry e) {
                    // Vanilla's falling-block path: the block is tesselated against the live level so it picks
                    // up the light of the position it is passing through, seeded by where it started.
                    net.minecraft.client.renderer.block.BlockRenderDispatcher dispatcher =
                            net.minecraft.client.Minecraft.getInstance().getBlockRenderer();
                    VertexConsumer buffer = bufferSource.getBuffer(
                            net.minecraft.client.renderer.ItemBlockRenderTypes.getMovingBlockRenderType(e.state()));
                    dispatcher.renderBatched(e.state(), e.pos(), e.level(), e.poseStack(), buffer, false,
                            net.minecraft.util.RandomSource.create(e.state().getSeed(e.seedPos())));
                } else if (entry instanceof BlockEntityEntry e) {
                    net.minecraft.client.Minecraft.getInstance().getBlockEntityRenderDispatcher()
                            .render(e.blockEntity(), e.partialTicks(), e.poseStack(), bufferSource);
                } else if (entry instanceof TextEntry e) {
                    e.font().drawInBatch(e.text(), e.x(), e.y(), e.color(), e.shadow(), e.poseStack().last().pose(),
                            bufferSource, e.mode(), e.background(), e.light());
                }
            }
        } finally {
            all.clear();
            root.sceneCaptured = false;
        }
    }
}
*///?}
