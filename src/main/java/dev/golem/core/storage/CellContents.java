package dev.golem.core.storage;

import dev.golem.core.autocraft.CraftingMachines;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** What an item storage cell (an AE2 storage or portable cell) holds. Empty for anything else, or without AE2. */
public final class CellContents {

    public record Stored(ItemStack type, long count) {
    }

    private CellContents() {}

    public static List<Stored> items(ItemStack cell) {
        //? if ae2 {
        if (CraftingMachines.available()) return dev.golem.core.ae2.Cells.items(cell);
        //?}
        return List.of();
    }
}
