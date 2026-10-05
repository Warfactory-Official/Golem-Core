package dev.golem.core.computer;

//? if computercraft {
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Where a mod's computer-facing blocks are: the peripheral a CC: Tweaked computer or modem finds at {@code pos}
 * through {@code side}, or null. Asked per query, so the answer follows the world without any invalidation.
 *
 * <p>{@code side} is the side of the block at {@code pos} that the computer or modem touches. {@code be} is the
 * block entity at {@code pos}, or null (a multiblock's folded cell has none). Only server levels are asked.
 */
@FunctionalInterface
public interface PeripheralPorts {

    @Nullable IPeripheral at(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity be,
                             @Nullable Direction side);
}
//?}
