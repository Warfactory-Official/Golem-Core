package dev.golem.core.world;

//? if <1.20.5 {
/*import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemStacksLegacyCarriedDataTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void readsBlockEntityInventoryAndCustomState() {
        ItemStack carrier = new ItemStack(Items.CHEST);
        CompoundTag carried = new CompoundTag();
        NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
        items.set(1, new ItemStack(Items.GOLD_INGOT));
        CompoundTag inventory = new CompoundTag();
        ContainerHelper.saveAllItems(inventory, items);
        carried.put("inventory", inventory);
        carried.putInt("stack", 321);
        carried.putBoolean("output", true);
        carrier.getOrCreateTag().put("BlockEntityTag", carried);

        assertTrue(ItemStacks.carriedItem(carrier, "inventory", 3, 1).is(Items.GOLD_INGOT));
        assertEquals(321, ItemStacks.carriedInt(carrier, "stack"));
        assertTrue(ItemStacks.carriedBoolean(carrier, "output"));
    }

    @Test
    void supportsRootAndDirectSlotFallbacksWithoutOverridingBlockEntityTag() {
        ItemStack rootCarrier = new ItemStack(Items.CHEST);
        rootCarrier.getOrCreateTag().put("slot1", new ItemStack(Items.IRON_INGOT).save(new CompoundTag()));
        rootCarrier.getOrCreateTag().putInt("stack", 12);
        assertTrue(ItemStacks.carriedItem(rootCarrier, "inventory", 3, 1).is(Items.IRON_INGOT));
        assertEquals(12, ItemStacks.carriedInt(rootCarrier, "stack"));
        assertFalse(ItemStacks.carriedBoolean(rootCarrier, "output"));

        CompoundTag carried = new CompoundTag();
        carried.putInt("stack", 7);
        rootCarrier.getOrCreateTag().put("BlockEntityTag", carried);
        assertEquals(7, ItemStacks.carriedInt(rootCarrier, "stack"));
        assertTrue(ItemStacks.carriedItem(rootCarrier, "inventory", 3, 1).isEmpty());
    }
}
*///?}
