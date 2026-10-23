package dev.golem.core.autocraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** The crafting machine reached at {@code pos} through {@code side}, or null. Asked per query on the server thread. */
@FunctionalInterface
public interface CraftingMachinePorts {

    @Nullable CraftingMachine at(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity be,
                                 @Nullable Direction side);
}
