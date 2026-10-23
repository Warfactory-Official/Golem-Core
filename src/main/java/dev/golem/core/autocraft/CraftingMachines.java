package dev.golem.core.autocraft;

import dev.golem.core.platform.Platform;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Publishes machines to AE2 pattern providers, which push whole jobs. AE2 exists on the NeoForge nodes and both 1.20.1
 * loaders; 1.20.1 Forge reaches only blocks with a block entity.
 */
public final class CraftingMachines {

    public static final String AE2 = "ae2";

    private CraftingMachines() {}

    public static boolean available() {
        //? if ae2 {
        return Platform.isModLoaded(AE2);
        //?} else {
        /*return false;
        *///?}
    }

    /** Call once from common init. */
    public static void exposeBlocks(CraftingMachinePorts ports, Predicate<Block> carriers) {
        //? if ae2 {
        if (available()) dev.golem.core.ae2.Lookups.exposeMachines(ports, carriers);
        //?}
    }

    /** False on 1.20.1 Forge, whose capabilities hang off block entities. */
    public static boolean reachesBlocksWithoutBlockEntity() {
        //? if forge {
        /*return false;
        *///?} else {
        return true;
        //?}
    }

    /** Whether AE2's own lookup finds a machine here. For tests. */
    public static boolean exposed(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        //? if ae2 {
        return available() && dev.golem.core.ae2.Lookups.machineExposed(level, pos, side);
        //?} else {
        /*return false;
        *///?}
    }
}
