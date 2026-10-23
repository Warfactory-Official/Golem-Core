package dev.golem.core.ae2;

//? if ae2 && fabric {
/*import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.storage.MEStorage;
import dev.golem.core.autocraft.CraftingMachinePorts;
import dev.golem.core.storage.ItemStorePorts;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

// Lookup fallbacks are asked for every block, folded cells included, so the carrier test runs per query.
final class FabricLookups {

    private FabricLookups() {}

    static void exposeMachines(CraftingMachinePorts ports, Predicate<Block> carriers) {
        ICraftingMachine.SIDED.registerFallback((level, pos, state, be, side) ->
                level instanceof ServerLevel server && carriers.test(state.getBlock())
                        ? MachineAdapter.of(ports.at(server, pos, state, be, side)) : null);
    }

    static boolean machineExposed(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return ICraftingMachine.of(level, pos, side, level.getBlockEntity(pos)) != null;
    }

    static void exposeStores(ItemStorePorts ports, Predicate<Block> carriers) {
        MEStorage.SIDED.registerFallback((level, pos, state, be, side) ->
                level instanceof ServerLevel server && carriers.test(state.getBlock())
                        ? StoreAdapter.of(ports.at(server, pos, state, be, side)) : null);
    }

    static @Nullable MEStorage findStore(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return MEStorage.SIDED.find(level, pos, side);
    }
}
*///?}
