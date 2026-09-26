package dev.golem.core.machine;

import dev.golem.core.data.DataIn;
import dev.golem.core.data.DataOut;
import dev.golem.core.multiblock.MultiblockCore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@link SyncedBlockEntity} with an {@link ItemHandler}, exposed as a vanilla {@link Container} for menus. Saved under
 * child {@code inventory}. {@link #stillValid}: a {@link MultiblockCore} measures its footprint, not the core cell.
 */
public abstract class InventoryBlockEntity extends SyncedBlockEntity implements Container {

    public final ItemHandler inventory;

    public InventoryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slotCount) {
        super(type, pos, state);
        this.inventory = createInventory(slotCount);
    }

    /** Override for per-slot limits; keep the two hooks wired. */
    protected ItemHandler createInventory(int slotCount) {
        return new ItemHandler(slotCount) {
            @Override
            protected void onContentsChanged(int slot) {
                InventoryBlockEntity.this.setChanged();
                InventoryBlockEntity.this.onSlotContentsChanged(slot, getStackInSlot(slot));
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return InventoryBlockEntity.this.isItemValidForSlot(slot, stack);
            }
        };
    }

    /** After {@link #setChanged()}. */
    protected void onSlotContentsChanged(int slot, ItemStack stack) {}

    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return true;
    }

    @Override
    protected void saveData(DataOut output) {
        super.saveData(output);
        inventory.save(output.child("inventory"));
    }

    @Override
    protected void loadData(DataIn input) {
        super.loadData(input);
        input.child("inventory").ifPresent(inventory::load);
    }

    @Override
    public int getContainerSize() {
        return inventory.getSlots();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(inventory.getStacks(), slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(inventory.getStacks(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        inventory.setStackInSlot(slot, stack);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isItemValidForSlot(slot, stack);
    }

    /** Plain block: 8-block radius (era-neutral spelling). */
    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) return false;
        if (level.getBlockState(worldPosition).getBlock() instanceof MultiblockCore) {
            return MultiblockCore.isWithinInteractionRange(level, worldPosition, player);
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < inventory.getSlots(); i++) inventory.setStackInSlot(i, ItemStack.EMPTY);
    }
}
