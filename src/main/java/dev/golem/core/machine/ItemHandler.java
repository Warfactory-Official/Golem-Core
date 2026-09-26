package dev.golem.core.machine;

import dev.golem.core.data.DataIn;
import dev.golem.core.data.DataOut;
import dev.golem.core.data.Inventories;
import dev.golem.core.world.ItemStacks;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/** Loader-free slot inventory; method surface = Forge {@code ItemStackHandler}. Saved as the {@code Items} list. */
public class ItemHandler {

    protected NonNullList<ItemStack> stacks;
    protected final int slotLimit;

    public ItemHandler(int size) {
        this(size, 64);
    }

    public ItemHandler(int size, int slotLimit) {
        this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
        this.slotLimit = slotLimit;
    }

    public int getSlots() {
        return stacks.size();
    }

    public ItemStack getStackInSlot(int slot) {
        return stacks.get(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        stacks.set(slot, stack);
        onContentsChanged(slot);
    }

    /** Per-slot cap before the item's own max stack size. */
    public int getSlotLimit(int slot) {
        return slotLimit;
    }

    protected int getStackLimit(int slot, ItemStack stack) {
        return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
    }

    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }

    protected void onContentsChanged(int slot) {}

    /** @return the remainder */
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!isItemValid(slot, stack)) return stack;

        ItemStack existing = stacks.get(slot);
        int limit = getStackLimit(slot, stack);

        if (!existing.isEmpty()) {
            if (!ItemStacks.sameItemAndData(stack, existing)) return stack;
            limit -= existing.getCount();
        }
        if (limit <= 0) return stack;

        int toInsert = Math.min(limit, stack.getCount());
        if (!simulate) {
            if (existing.isEmpty()) stacks.set(slot, ItemStacks.copyWithCount(stack, toInsert));
            else existing.grow(toInsert);
            onContentsChanged(slot);
        }

        int remainder = stack.getCount() - toInsert;
        return remainder <= 0 ? ItemStack.EMPTY : ItemStacks.copyWithCount(stack, remainder);
    }

    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        ItemStack existing = stacks.get(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;

        int toExtract = Math.min(amount, existing.getMaxStackSize());
        if (existing.getCount() <= toExtract) {
            if (!simulate) {
                stacks.set(slot, ItemStack.EMPTY);
                onContentsChanged(slot);
            }
            return simulate ? existing.copy() : existing;
        }
        if (!simulate) {
            stacks.set(slot, ItemStacks.copyWithCount(existing, existing.getCount() - toExtract));
            onContentsChanged(slot);
        }
        return ItemStacks.copyWithCount(existing, toExtract);
    }

    public NonNullList<ItemStack> getStacks() {
        return stacks;
    }

    public void save(DataOut output) {
        Inventories.save(output, stacks, true);
    }

    public void load(DataIn input) {
        Inventories.load(input, stacks);
    }
}
