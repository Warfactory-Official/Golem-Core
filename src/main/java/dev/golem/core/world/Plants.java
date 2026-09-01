package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Cross-era plant growth queries. */
public final class Plants {

    private Plants() {}

    public static boolean isValidBonemealTarget(BonemealableBlock block, Level level,
                                                 BlockPos pos, BlockState state) {
        //? if >=1.21.1 {
        return block.isValidBonemealTarget(level, pos, state);
        //?} else {
        /*return block.isValidBonemealTarget(level, pos, state, false);
        *///?}
    }
}
