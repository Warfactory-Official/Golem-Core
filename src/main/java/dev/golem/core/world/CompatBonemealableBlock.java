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

    //? if >=26.3 {
    /*/^* Vanilla's 26.2 shape, kept so implementers need not name 26.3's {@code BonemealSource}. ^/
    boolean isBonemealSuccess(net.minecraft.world.level.Level level, net.minecraft.util.RandomSource random, BlockPos pos,
                              BlockState state);

    void performBonemeal(net.minecraft.server.level.ServerLevel level, net.minecraft.util.RandomSource random, BlockPos pos,
                         BlockState state);

    @Override
    default boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state,
                                          net.minecraft.world.level.block.BonemealSource source) {
        return canBonemeal(level, pos, state);
    }

    @Override
    default boolean isBonemealSuccess(net.minecraft.world.level.Level level, net.minecraft.util.RandomSource random,
                                      BlockPos pos, BlockState state, net.minecraft.world.level.block.BonemealSource source) {
        return isBonemealSuccess(level, random, pos, state);
    }

    @Override
    default void performBonemeal(net.minecraft.server.level.ServerLevel level, net.minecraft.util.RandomSource random,
                                 BlockPos pos, BlockState state, net.minecraft.world.level.block.BonemealSource source) {
        performBonemeal(level, random, pos, state);
    }
    *///?} elif >=1.21.1 {
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
