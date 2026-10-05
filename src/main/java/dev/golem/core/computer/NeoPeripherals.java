package dev.golem.core.computer;

//? if computercraft && neoforge {
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jspecify.annotations.Nullable;

// NeoForge 26.1 and 1.21.1: CC's PeripheralCapability is an ordinary block capability, so blocks without a block
// entity (folded cells) can answer as well. The capability cache NeoForge keeps per position is invalidated by
// block changes, which is when a machine's answer changes.
final class NeoPeripherals {

    private NeoPeripherals() {}

    static void exposeBlocks(PeripheralPorts ports, Predicate<Block> carriers) {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener((RegisterCapabilitiesEvent event) -> {
            Block[] blocks = BuiltInRegistries.BLOCK.stream().filter(carriers).toArray(Block[]::new);
            if (blocks.length == 0) return;
            event.registerBlock(PeripheralCapability.get(), (level, pos, state, be, side) ->
                    level instanceof ServerLevel server ? ports.at(server, pos, state, be, side) : null, blocks);
        });
    }

    static @Nullable IPeripheral find(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(PeripheralCapability.get(), pos, side);
    }
}
//?}
