package dev.golem.core.render;

//? if <26.1 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

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

    private record Entry(int order, PoseStack.Pose pose, RenderType renderType, CustomGeometry geometry) {}

    // Items cannot be replayed through a VertexConsumer like geometry can — ItemRenderer wants the
    // whole MultiBufferSource — so they are recorded separately but sorted into the same order stream.
    private record ItemEntry(int order, PoseStack poseStack, CompatItemRenderState item, int light, int overlay) {}

    private final List<Entry> entries = new ArrayList<>();
    private final List<ItemEntry> items = new ArrayList<>();
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

    private List<Entry> sink() {
        return sharedWith != null ? sharedWith.sink() : entries;
    }

    private List<ItemEntry> itemSink() {
        return sharedWith != null ? sharedWith.itemSink() : items;
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometry geometry) {
        // copy(), not last(). PoseStack.last() hands back the LIVE top Pose, and translate/mulPose/scale
        // mutate it in place — so keeping the reference means every entry recorded at the same stack
        // level is drawn with whatever that pose held when the batch was finally flushed, not with the
        // transform it was submitted under. A renderer that accumulates joint transforms between
        // submissions (the assembly machine's arms) therefore drew its parts on top of each other.
        sink().add(new Entry(order, poseStack.last().copy(), renderType, geometry));
    }

    @Override
    public void submitItem(PoseStack poseStack, CompatItemRenderState item, int light, int overlay, int seed) {
        if (item.isEmpty()) return;
        // The PoseStack is copied, not snapshotted to a Pose: ItemRenderer needs a full stack to push
        // and pop around its own transforms, and the caller is free to pop before the flush.
        PoseStack copy = new PoseStack();
        copy.mulPose(poseStack.last().pose());
        itemSink().add(new ItemEntry(order, copy, item, light, overlay));
    }

    @Override
    public SubmitSink order(int newOrder) {
        return new RecordingSubmitSink(newOrder, this);
    }

    /^* Draws everything recorded, lowest order first, then clears. ^/
    public void flush(MultiBufferSource bufferSource, net.minecraft.world.level.@org.jspecify.annotations.Nullable Level level) {
        List<Entry> all = sink();
        all.sort(Comparator.comparingInt(Entry::order));
        for (Entry e : all) {
            e.geometry().render(e.pose(), bufferSource.getBuffer(e.renderType()));
        }
        all.clear();

        List<ItemEntry> allItems = itemSink();
        allItems.sort(Comparator.comparingInt(ItemEntry::order));
        for (ItemEntry e : allItems) {
            e.item().draw(e.poseStack(), bufferSource, e.light(), e.overlay(), level);
        }
        allItems.clear();
    }
}
*///?}
