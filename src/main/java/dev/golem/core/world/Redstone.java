package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Redstone operations whose comparator call shape differs across supported Minecraft eras. */
public final class Redstone {
    private Redstone() {}

    /** Reads the 1.12.2 torch input: comparator override when present, otherwise ordinary signal. */
    public static int read(Level level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        int value = level.getSignal(pos, direction);
        if (state.hasAnalogOutputSignal()) {
            //? if >=26.1 {
            value = state.getAnalogOutputSignal(level, pos, direction);
            //?} else {
            /*value = state.getAnalogOutputSignal(level, pos);
            *///?}
        }
        return Mth.clamp(value, 0, 15);
    }
}
