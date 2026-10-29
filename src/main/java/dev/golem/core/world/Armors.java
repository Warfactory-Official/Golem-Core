package dev.golem.core.world;

import dev.golem.core.Ids;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
//? if >=26.1 {
import net.minecraft.world.item.equipment.EquipmentAssets;
//?}
//? if >=26.1 {
import net.minecraft.world.item.equipment.Equippable;
//?}

import net.minecraft.world.item.ItemStack;

/**
 * Which armour piece any item stack is - vanilla's, another mod's or a {@link CompatArmorItem} - the legacy
 * {@code item instanceof ItemArmor} / {@code ItemArmor.armorType} test.
 *
 * <p>Below 26.1 that is still a class test ({@code ArmorItem} and its {@code Type}). 26.1 removed the class: armour is a
 * plain item carrying an {@code EQUIPPABLE} component for a humanoid armour slot and armour attribute modifiers (what
 * {@code Item.Properties.humanoidArmor} sets). The attribute test keeps out the other things a 26.1 humanoid slot can
 * equip - carved pumpkins, mob heads, the elytra - none of which were {@code ItemArmor} in 1.7 either.
 */
public final class Armors {

    private Armors() {}

    /** The armour piece {@code stack} is, or null for anything that is not armour (empty, wolf/horse armour, elytra, ...). */
    public static ArmorSlot slotOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof CompatArmorItem armor) return armor.armorSlot();
        //? if >=26.1 {
        net.minecraft.world.item.equipment.Equippable equippable =
                stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        if (equippable == null) return null;
        ArmorSlot slot = switch (equippable.slot()) {
            case HEAD -> ArmorSlot.HELMET;
            case CHEST -> ArmorSlot.CHESTPLATE;
            case LEGS -> ArmorSlot.LEGGINGS;
            case FEET -> ArmorSlot.BOOTS;
            default -> null;
        };
        if (slot == null) return null;
        net.minecraft.world.item.component.ItemAttributeModifiers modifiers = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        for (net.minecraft.world.item.component.ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().value() == net.minecraft.world.entity.ai.attributes.Attributes.ARMOR.value()
                    || entry.attribute().value() == net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS.value()) {
                return slot;
            }
        }
        return null;
        //?} else {
        /*if (!(stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor)) return null;
        return switch (armor.getType()) {
            case HELMET -> ArmorSlot.HELMET;
            case CHESTPLATE -> ArmorSlot.CHESTPLATE;
            case LEGGINGS -> ArmorSlot.LEGGINGS;
            case BOOTS -> ArmorSlot.BOOTS;
            default -> null;
        };
        *///?}
    }

    /** 26.1's equipment component of a {@link Wearable}; not dispensable: dispensers before 26.1 drop it. */
    public static Item.Properties equippable(
            Item.Properties properties, ArmorSlot slot, String asset) {
        //? if >=26.1 {
        return properties.component(DataComponents.EQUIPPABLE,
                Equippable.builder(equipmentSlot(slot))
                        .setAsset(ResourceKey.create(
                                EquipmentAssets.ROOT_ID,
                                Ids.parse(asset)))
                        .setDispensable(false)
                        .build());
        //?} else {
        /*return properties;
        *///?}
    }

    /** Whether {@code stack} is armour of any kind (see {@link #slotOf}). */
    public static boolean isArmor(ItemStack stack) {
        return slotOf(stack) != null;
    }

    /** The vanilla equipment slot an armour piece is worn in. */
    public static net.minecraft.world.entity.EquipmentSlot equipmentSlot(ArmorSlot slot) {
        return switch (slot) {
            case HELMET -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case CHESTPLATE -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case LEGGINGS -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case BOOTS -> net.minecraft.world.entity.EquipmentSlot.FEET;
        };
    }
}
