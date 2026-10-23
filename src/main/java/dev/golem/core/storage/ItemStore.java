package dev.golem.core.storage;

import java.util.function.ObjLongConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A block's items as a storage network sees them. {@code type} stacks have a count of 1; amounts are in items. */
public interface ItemStore {

    /** How many of {@code amount} were taken in. */
    long insert(ItemStack type, long amount, boolean simulate);

    /** How many of {@code amount} were handed out. */
    long extract(ItemStack type, long amount, boolean simulate);

    void list(ObjLongConsumer<ItemStack> out);

    Component name();

    /** Whether the network should fill this store before others with {@code type}. */
    default boolean prefers(ItemStack type) {
        return false;
    }
}
