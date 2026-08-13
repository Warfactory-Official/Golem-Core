package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

//? if >=26.1 {
import net.minecraft.server.level.ServerLevel;
//?} else {
/*import net.minecraft.world.level.Level;
*///?}

/**
 * A {@link Block} that runs its block entity's removal side effects on every version.
 *
 * <p>26.1 calls {@code BlockEntity.preRemoveSideEffects} itself when a block entity goes away, and its
 * default drops the contents of any {@code Container}. Nothing does that below 26.1 — dropping is each
 * block's own business — so a block whose entity holds an inventory silently voids it when broken. The
 * hook that replaced it is also spelled differently per era ({@code affectNeighborsAfterRemoval} versus
 * {@code onRemove}), which is the other half of why this is worth a base class rather than fifteen
 * copies of the same guarded override.
 *
 * <p>Extend this instead of {@code Block} for any block with an inventory-bearing block entity. A
 * subclass that overrides the removal hook itself must call {@code super}.
 *
 * @see Compat#preRemoveSideEffects
 */
public class CompatBlock extends Block {

    public CompatBlock(Properties properties) {
        super(properties);
    }

    @Override
    //? if >=26.1 {
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Compat.preRemoveSideEffects(level, pos, state);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
    //?} else {
    /*protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Only when the block is actually going away, not on a state change that keeps it — otherwise a
        // machine would drop its inventory every time one of its blockstate properties flipped.
        if (!state.is(newState.getBlock())) Compat.preRemoveSideEffects(level, pos, state);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    *///?}
}
