package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluid;

/** Version-neutral access to scheduling a fluid tick from a block neighbour callback. */
@FunctionalInterface
public interface BlockTickScheduler {

    void scheduleTick(BlockPos pos, Fluid fluid, int delay);
}
