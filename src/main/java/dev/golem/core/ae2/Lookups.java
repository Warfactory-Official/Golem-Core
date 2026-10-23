package dev.golem.core.ae2;

//? if ae2 {
import dev.golem.core.autocraft.CraftingMachinePorts;
import dev.golem.core.storage.ItemStore;
import dev.golem.core.storage.ItemStorePorts;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

public final class Lookups {

    private Lookups() {}

    public static void exposeMachines(CraftingMachinePorts ports, Predicate<Block> carriers) {
        //? if neoforge {
        NeoLookups.exposeMachines(ports, carriers);
        //?} elif forge {
        /*ForgeLookups.exposeMachines(ports, carriers);
        *///?} else {
        /*FabricLookups.exposeMachines(ports, carriers);
        *///?}
    }

    public static boolean machineExposed(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        //? if neoforge {
        return NeoLookups.machineExposed(level, pos, side);
        //?} elif forge {
        /*return ForgeLookups.machineExposed(level, pos, side);
        *///?} else {
        /*return FabricLookups.machineExposed(level, pos, side);
        *///?}
    }

    public static void exposeStores(ItemStorePorts ports, Predicate<Block> carriers) {
        //? if neoforge {
        NeoLookups.exposeStores(ports, carriers);
        //?} elif forge {
        /*ForgeLookups.exposeStores(ports, carriers);
        *///?} else {
        /*FabricLookups.exposeStores(ports, carriers);
        *///?}
    }

    public static @Nullable ItemStore findStore(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        //? if neoforge {
        return StoreAdapter.view(NeoLookups.findStore(level, pos, side));
        //?} elif forge {
        /*return StoreAdapter.view(ForgeLookups.findStore(level, pos, side));
        *///?} else {
        /*return StoreAdapter.view(FabricLookups.findStore(level, pos, side));
        *///?}
    }
}
//?}
