package dev.golem.core.ae2;

//? if ae2 {
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.StorageCell;
import dev.golem.core.storage.CellContents;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

// Basic and portable item cells only, never the creative cell, whose contents are not real.
public final class Cells {

    private Cells() {}

    public static List<CellContents.Stored> items(ItemStack cell) {
        if (!(cell.getItem() instanceof IBasicCellItem basic) || basic.getKeyType() != AEKeyType.items()) return List.of();
        StorageCell inventory = StorageCells.getCellInventory(cell, null);
        if (inventory == null) return List.of();
        List<CellContents.Stored> stored = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : inventory.getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey item) stored.add(new CellContents.Stored(item.toStack(), entry.getLongValue()));
        }
        return stored;
    }
}
//?}
