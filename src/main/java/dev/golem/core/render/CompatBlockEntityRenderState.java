package dev.golem.core.render;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
//? if >=1.21.11 {
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
//?}

/**
 * A block entity that is not in the world (one carried by a falling structure, say) prepared for
 * drawing through its own registered renderer via {@link SubmitSink#submitBlockEntity}.
 *
 * <p>26.1 must extract the block entity's render state in the extract pass and submit that state with
 * the camera later; older versions call the dispatcher's immediate {@code render} at draw time. The
 * block entity must have its level set on both eras, since renderers read it for lighting.
 */
public final class CompatBlockEntityRenderState {

    //? if >=1.21.11 {
    @Nullable BlockEntityRenderState delegate;
    //?} else {
    /*@Nullable BlockEntity blockEntity;
    float partialTicks;
    *///?}

    public boolean isEmpty() {
        //? if >=1.21.11 {
        return delegate == null;
        //?} else {
        /*return blockEntity == null;
        *///?}
    }

    public void clear() {
        //? if >=1.21.11 {
        delegate = null;
        //?} else {
        /*blockEntity = null;
        *///?}
    }

    /** Prepares {@code blockEntity}; a block entity with no registered renderer clears. */
    public void extract(@Nullable BlockEntity blockEntity, float partialTicks) {
        if (blockEntity == null) {
            clear();
            return;
        }
        //? if >=1.21.11 {
        //? if >=26.2 {
        /*var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(blockEntity);
        // 26.2 extracts only when the flag matches the renderer's own off-screen rule; previews take either kind.
        delegate = renderer == null ? null
                : dispatcher.tryExtractRenderState(blockEntity, partialTicks, null, renderer.shouldRenderOffScreen());
        *///?} else {
        delegate = Minecraft.getInstance().getBlockEntityRenderDispatcher()
                .tryExtractRenderState(blockEntity, partialTicks, null);
        //?}
        //?} else {
        /*this.blockEntity = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity) == null
                ? null : blockEntity;
        this.partialTicks = partialTicks;
        *///?}
    }
}
