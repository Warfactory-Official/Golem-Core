package dev.golem.core.render;

import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

//? if >=26.1 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
//?}

/**
 * A block state prepared for drawing as a free-standing model (a mob's disguise, a cart's display
 * block) through {@link SubmitSink#submitBlock}.
 *
 * <p>The block-side twin of {@link CompatItemRenderState}: 26.1 resolves the state's model during
 * extraction into a {@code BlockModelRenderState} and submits it later, while older versions hand the
 * {@code BlockState} to {@code BlockRenderDispatcher#renderSingleBlock} at draw time. This holds
 * whichever the target needs so a renderer keeps the two-pass shape on every node.
 */
public final class CompatBlockRenderState {

    //? if >=26.1 {
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    // Minecraft's own resolver is private on NeoForge; the class is a stateless view over the model
    // manager, so a second instance is equivalent.
    private static net.minecraft.client.renderer.block.@Nullable BlockModelResolver resolver;
    final BlockModelRenderState delegate = new BlockModelRenderState();
    //?} else {
    /*@Nullable BlockState state;
    *///?}

    private boolean empty = true;

    public boolean isEmpty() {
        return empty;
    }

    /** Forgets any extracted block. */
    public void clear() {
        empty = true;
        //? if >=26.1 {
        delegate.clear();
        //?} else {
        /*state = null;
        *///?}
    }

    /** Prepares {@code block} for drawing. Safe every frame; null or air clears. */
    public void extract(@Nullable BlockState block) {
        if (block == null || block.isAir()) {
            clear();
            return;
        }
        empty = false;
        //? if >=26.1 {
        net.minecraft.client.renderer.block.BlockModelResolver r = resolver;
        if (r == null) resolver = r = new net.minecraft.client.renderer.block.BlockModelResolver(
                Minecraft.getInstance().getModelManager());
        r.update(delegate, block, DISPLAY);
        //?} else {
        /*this.state = block;
        *///?}
    }
}
