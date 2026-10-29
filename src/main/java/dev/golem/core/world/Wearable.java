package dev.golem.core.world;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
//? if >=26.1 {
import net.minecraft.core.component.DataComponents;
//?} else {
/*import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
*///?}

/**
 * A non-armour item worn in one armour slot (1.7 {@code isValidArmor}): the armour slot, shift-click and mobs take it,
 * and {@link #wear} equips it from the hand. Give it {@link Armors#equippable} for the same slot, which 26.1 reads.
 */
//? if >=26.1 {
public interface Wearable {
//?} else {
/*public interface Wearable extends Equipable {
*///?}

    ArmorSlot wornSlot();

    /** Swaps the stack in {@code hand} with the one worn in its slot, for the item's use. */
    default InteractionResult wear(Level level, Player player, InteractionHand hand) {
        //? if >=26.1 {
        ItemStack held = player.getItemInHand(hand);
        var equippable = held.get(DataComponents.EQUIPPABLE);
        // As vanilla's Item.use: a command can strip the component from one stack.
        return equippable != null && equippable.swappable() ? equippable.swapWithEquipmentSlot(held, player)
                : InteractionResult.PASS;
        //?} else {
        /*InteractionResultHolder<ItemStack> result = swapWithEquipmentSlot((Item) this, level, player, hand);
        // CompatItem answers use with the stack left in the hand, so the piece taken off goes there.
        player.setItemInHand(hand, result.getObject());
        return result.getResult();
        *///?}
    }

    //? if <26.1 {
    /*@Override
    default EquipmentSlot getEquipmentSlot() {
        return Armors.equipmentSlot(wornSlot());
    }
    *///?}
}
