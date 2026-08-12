package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

//? if >=26.1 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
*///?}

/**
 * An item stack prepared for drawing inside a block-entity renderer — a machine's floating output
 * preview, a mould's contents.
 *
 * <p>The two eras split the work differently. 26.1 resolves the stack's model during the extract pass
 * into an {@code ItemStackRenderState} and submits that later, so nothing touches the item registry on
 * the render thread. Older versions have no such split and simply hand the {@code ItemStack} to
 * {@code ItemRenderer} at draw time.
 *
 * <p>This type holds whichever of the two the target version needs, so renderers can keep 26.1's
 * shape — {@link #extract} during the extract pass, {@link SubmitSink#submitItem} during submit —
 * without either side leaking a version-specific type into their fields.
 */
public final class CompatItemRenderState {

    //? if >=26.1 {
    final ItemStackRenderState delegate = new ItemStackRenderState();
    //?} else {
    /*@Nullable ItemStack stack;
    *///?}

    /** True when there is nothing to draw. */
    public boolean isEmpty() {
        //? if >=26.1 {
        return delegate.isEmpty();
        //?} else {
        /*return stack == null || stack.isEmpty();
        *///?}
    }

    /** Forgets any previously extracted item. */
    public void clear() {
        //? if >=26.1 {
        delegate.clear();
        //?} else {
        /*stack = null;
        *///?}
    }

    /**
     * Prepares {@code stack} for drawing. Safe to call every frame; a null or empty stack clears.
     *
     * @param context the display context the item is drawn in, e.g. {@code ItemDisplayContext.FIXED}
     */
    public void extract(@Nullable Level level, @Nullable ItemStack stack, ItemDisplayContext context) {
        if (stack == null || stack.isEmpty()) {
            clear();
            return;
        }
        //? if >=26.1 {
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(delegate, stack, context, level, null, 0);
        //?} else {
        /*this.stack = stack.copy();
        *///?}
    }

    //? if <26.1 {
    /*/^* Draws immediately; pre-26.1 item rendering is not deferred. ^/
    void draw(PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay, @Nullable Level level) {
        if (stack == null || stack.isEmpty()) return;
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.FIXED, light, overlay, poseStack, bufferSource, level, 0);
    }
    *///?}
}
