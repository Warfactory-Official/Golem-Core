package dev.golem.core.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Where a mod's fluid machines are: the face at {@code pos} on {@code side}, or null where a foreign pipe
 * would find nothing. Asked per query, so the answer follows the world without any invalidation.
 *
 * <p>{@code side} is the side of the block at {@code pos} that is being asked through; null is a sideless
 * query (a probe or a tooltip), which an implementation may answer read-only.
 */
@FunctionalInterface
public interface FluidPorts {

    @Nullable FluidMachine at(ServerLevel level, BlockPos pos, @Nullable Direction side);
}
