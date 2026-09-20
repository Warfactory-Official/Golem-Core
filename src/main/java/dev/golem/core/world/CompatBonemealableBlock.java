package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block-side half of {@link Plants}: a block that bonemeal can grow.
 *
 * <p>Vanilla's {@link BonemealableBlock#isValidBonemealTarget} took a trailing {@code isClient}
 * flag up to 1.20.1 and dropped it in 1.21; the other two methods never changed. Implement
 * {@link #canBonemeal} once and this maps it onto whichever spelling the node has.
 */
public interface CompatBonemealableBlock extends BonemealableBlock {

    /** The old {@code IGrowable.func_149851_a}: whether the plant has room / conditions to grow. */
    boolean canBonemeal(LevelReader level, BlockPos pos, BlockState state);

    //? if >=1.21.1 {
    @Override
    default boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return canBonemeal(level, pos, state);
    }
    //?} else {
    /*@Override
    default boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean isClient) {
        return canBonemeal(level, pos, state);
    }
    *///?}
}
