package dev.golem.core.world;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Write access to a falling block's private carried state, exposed by {@code MixinFallingBlockState}
 * for custom falling blocks that decide their state after construction (vanilla only assigns it in a
 * private constructor).
 */
public interface FallingBlockState {

    void golem$setBlockState(BlockState state);

    /** Whether {@code disableDrop()} was called: vanilla keeps the flag private with no getter. */
    boolean golem$cancelDrop();
}
