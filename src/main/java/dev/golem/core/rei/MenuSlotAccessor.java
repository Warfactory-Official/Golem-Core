package dev.golem.core.rei;

//? if rei {

import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor;
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessorRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A recipe slot of a mod's menu as a REI transfer target, with JEI's acceptance rule.
 *
 * <p>REI's own {@code SlotAccessor.fromSlot} asks vanilla's {@code Slot.allowModification}, which is
 * {@code mayPickup && mayPlace(current stack)}: for an empty machine slot whose {@code mayPlace} accepts only the
 * machine's valid inputs, {@code mayPlace(EMPTY)} is false and REI's server refuses the transfer ("Slot ... is not
 * modifiable"), although the ingredient itself would go in. JEI's transfer asks {@code mayPlace} of the stack it moves;
 * this accessor does the same: modifiable when the slot may be picked from, placeable when it accepts the stack. It
 * travels to the server by its menu index ({@code golemcore:menu_slot}, registered by {@link ReiCommonPlugin}).
 */
final class MenuSlotAccessor implements SlotAccessor {

    static final String ID = "menu_slot";

    private final Slot slot;

    MenuSlotAccessor(Slot slot) {
        this.slot = slot;
    }

    @Override
    public ItemStack getItemStack() {
        return slot.getItem();
    }

    @Override
    public void setItemStack(ItemStack stack) {
        slot.set(stack);
    }

    @Override
    public ItemStack takeStack(int amount) {
        return slot.remove(amount);
    }

    @Override
    public boolean allowModification(Player player) {
        return slot.mayPickup(player);
    }

    @Override
    public boolean canPlace(ItemStack stack) {
        return slot.mayPlace(stack);
    }

    static void register(SlotAccessorRegistry registry) {
        registry.register(dev.golem.core.Ids.of("golemcore", ID), accessor -> accessor instanceof MenuSlotAccessor,
                new SlotAccessorRegistry.Serializer() {
                    @Override
                    public SlotAccessor read(net.minecraft.world.inventory.AbstractContainerMenu menu, Player player, CompoundTag tag) {
                        //? if >=26.1 {
                        int index = tag.getIntOr("Slot", -1);
                        //?} else {
                        /*int index = tag.getInt("Slot");
                        *///?}
                        return new MenuSlotAccessor(menu.getSlot(index));
                    }

                    @Override
                    public CompoundTag save(net.minecraft.world.inventory.AbstractContainerMenu menu, Player player, SlotAccessor accessor) {
                        CompoundTag tag = new CompoundTag();
                        tag.putInt("Slot", ((MenuSlotAccessor) accessor).slot.index);
                        return tag;
                    }
                });
    }
}
//?}
