package dev.golem.core.data;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Immutable fixed-slot contents with item/data equality, suitable for an item component. */
public final class ItemContents {
    public static final Codec<ItemContents> CODEC = GolemComponent.itemStackListCodec()
            .xmap(ItemContents::new, ItemContents::copies);
    public static final ItemContents EMPTY = new ItemContents(List.of());
    private final List<ItemStack> stacks;

    public ItemContents(List<ItemStack> stacks) {
        this.stacks = stacks.stream().map(ItemStack::copy).toList();
    }

    public List<ItemStack> copies() {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ItemContents contents) || stacks.size() != contents.stacks.size()) return false;
        for (int i = 0; i < stacks.size(); i++) {
            if (!ItemStack.matches(stacks.get(i), contents.stacks.get(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (ItemStack stack : stacks) {
            int value = 0;
            if (!stack.isEmpty()) {
                //? if >=1.21.1 {
                value = 31 * ItemStack.hashItemAndComponents(stack) + stack.getCount();
                //?} else {
                /*value = 31 * java.util.Objects.hash(stack.getItem(), stack.getTag()) + stack.getCount();
                *///?}
            }
            hash = 31 * hash + value;
        }
        return hash;
    }
}
