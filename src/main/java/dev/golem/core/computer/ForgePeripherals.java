package dev.golem.core.computer;

//? if computercraft && forge {
/*import dan200.computercraft.api.ForgeComputerCraftAPI;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.LazyOptional;
import org.jspecify.annotations.Nullable;

// 1.20.1 Forge: CC asks registered providers for any position before a block entity's own capability, so this
// reaches folded cells, which carry no block entity to attach a Forge capability to. CC asks again on neighbour
// changes, which is when a machine's answer changes; the optional is never invalidated by hand.
final class ForgePeripherals {

    private static final List<IPeripheralProvider> PROVIDERS = new ArrayList<>();

    private ForgePeripherals() {}

    static void exposeBlocks(PeripheralPorts ports, Predicate<Block> carriers) {
        IPeripheralProvider provider = (level, pos, side) -> {
            if (!(level instanceof ServerLevel server)) return LazyOptional.empty();
            BlockState state = server.getBlockState(pos);
            if (!carriers.test(state.getBlock())) return LazyOptional.empty();
            IPeripheral peripheral = ports.at(server, pos, state, server.getBlockEntity(pos), side);
            return peripheral == null ? LazyOptional.empty() : LazyOptional.of(() -> peripheral);
        };
        PROVIDERS.add(provider);
        ForgeComputerCraftAPI.registerPeripheralProvider(provider);
    }

    static @Nullable IPeripheral find(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        for (IPeripheralProvider provider : PROVIDERS) {
            IPeripheral peripheral = provider.getPeripheral(level, pos, side).resolve().orElse(null);
            if (peripheral != null) return peripheral;
        }
        return null;
    }
}
*///?}
