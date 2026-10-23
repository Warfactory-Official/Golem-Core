package dev.golem.core.storage;

import dev.golem.core.autocraft.CraftingMachines;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Publishes blocks as storage an AE2 storage bus reads directly, instead of through their item slots. Same nodes and
 * the same 1.20.1 Forge limit as {@link CraftingMachines}.
 */
public final class ItemStores {

    private ItemStores() {}

    /** Call once from common init. */
    public static void exposeBlocks(ItemStorePorts ports, Predicate<Block> carriers) {
        //? if ae2 {
        if (CraftingMachines.available()) dev.golem.core.ae2.Lookups.exposeStores(ports, carriers);
        //?}
    }

    /** The store AE2's own lookup finds here, read back through AE2's interface; null without one. For tests. */
    public static @Nullable ItemStore find(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        //? if ae2 {
        return CraftingMachines.available() ? dev.golem.core.ae2.Lookups.findStore(level, pos, side) : null;
        //?} else {
        /*return null;
        *///?}
    }
}
