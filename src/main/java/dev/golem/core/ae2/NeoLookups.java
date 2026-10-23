package dev.golem.core.ae2;

//? if ae2 && neoforge {
import appeng.api.AECapabilities;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.storage.MEStorage;
import dev.golem.core.autocraft.CraftingMachinePorts;
import dev.golem.core.platform.NeoForgeEntrypoint;
import dev.golem.core.storage.ItemStorePorts;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jspecify.annotations.Nullable;

// Block capabilities, so folded multiblock cells without a block entity answer too.
final class NeoLookups {

    private NeoLookups() {}

    static void exposeMachines(CraftingMachinePorts ports, Predicate<Block> carriers) {
        NeoForgeEntrypoint.modBus().addListener((RegisterCapabilitiesEvent event) -> {
            Block[] blocks = BuiltInRegistries.BLOCK.stream().filter(carriers).toArray(Block[]::new);
            if (blocks.length == 0) return;
            event.registerBlock(AECapabilities.CRAFTING_MACHINE, (level, pos, state, be, side) ->
                    level instanceof ServerLevel server ? MachineAdapter.of(ports.at(server, pos, state, be, side)) : null,
                    blocks);
        });
    }

    static boolean machineExposed(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return ICraftingMachine.of(level, pos, side) != null;
    }

    static void exposeStores(ItemStorePorts ports, Predicate<Block> carriers) {
        NeoForgeEntrypoint.modBus().addListener((RegisterCapabilitiesEvent event) -> {
            Block[] blocks = BuiltInRegistries.BLOCK.stream().filter(carriers).toArray(Block[]::new);
            if (blocks.length == 0) return;
            event.registerBlock(AECapabilities.ME_STORAGE, (level, pos, state, be, side) ->
                    level instanceof ServerLevel server ? StoreAdapter.of(ports.at(server, pos, state, be, side)) : null,
                    blocks);
        });
    }

    static @Nullable MEStorage findStore(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(AECapabilities.ME_STORAGE, pos, side);
    }
}
//?}
