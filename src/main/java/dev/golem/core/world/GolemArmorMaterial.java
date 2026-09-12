package dev.golem.core.world;

import dev.golem.core.Ids;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Version-neutral armour material declaration.
 *
 * <p>Consumers declare the material's stable data once. The native material and its asset/layer
 * representation are deliberately built here so vanilla armour APIs do not escape this facade.
 */
public final class GolemArmorMaterial {
    private final String namespace;
    private final String name;
    private final int durability;
    private final int helmetDefense;
    private final int chestDefense;
    private final int leggingsDefense;
    private final int bootsDefense;
    private final int enchantmentValue;
    private final float toughness;
    private final float knockbackResistance;
    private final String repairIngredient;
    private final Object nativeMaterial;

    public GolemArmorMaterial(String namespace, String name, int durability, int helmetDefense,
                              int chestDefense, int leggingsDefense, int bootsDefense,
                              int enchantmentValue, float toughness, float knockbackResistance,
                              String repairIngredient) {
        this.namespace = namespace;
        this.name = name;
        this.durability = durability;
        this.helmetDefense = helmetDefense;
        this.chestDefense = chestDefense;
        this.leggingsDefense = leggingsDefense;
        this.bootsDefense = bootsDefense;
        this.enchantmentValue = enchantmentValue;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repairIngredient = repairIngredient;
        this.nativeMaterial = createNative();
    }

    Object nativeMaterial() {
        return nativeMaterial;
    }

    /**
     * The enchantment value, where {@code 0} means "cannot be enchanted at all".
     *
     * <p>Read by {@link CompatArmorItem}, which has to special-case that zero: from 26.1 the enchantable
     * data component <b>rejects</b> a value of 0 outright ({@code Enchantment value must be positive}),
     * so an unenchantable armour must omit the component rather than declare it as zero. Below 26.1 the
     * same zero is simply the material's answer and needs no special handling.
     */
    int enchantmentValue() {
        return enchantmentValue;
    }

    /**
     * The material's base durability, before the per-slot multiplier.
     *
     * <p>Read by {@link CompatArmorItem} on 1.21.x only. 26.1's {@code ArmorMaterial} carries durability
     * itself and 1.20.1's interface answers {@code getDurabilityForType}, but 1.21.x's {@code
     * ArmorMaterial} record has no durability at all — there it is the item's own {@code
     * Properties.durability}, and nothing applies it on the consumer's behalf.
     */
    int durability() {
        return durability;
    }

    //? if >=26.1 {
    private TagKey<Item> repairTag() {
        return TagKey.create(Registries.ITEM,
                Ids.of(namespace, repairIngredient));
    }
    //?} else if >=1.21.1 {
    /*private TagKey<Item> repairTag() {
        return TagKey.create(Registries.ITEM,
                Ids.of(namespace, repairIngredient));
    }
    *///?} else {
    /*private TagKey<Item> repairTag() {
        return TagKey.create(Registries.ITEM,
                Ids.of(namespace, repairIngredient));
    }
    *///?}

    //? if >=26.1 {
    private net.minecraft.world.item.equipment.ArmorMaterial createNative() {
        Map<net.minecraft.world.item.equipment.ArmorType, Integer> defense =
                new EnumMap<>(net.minecraft.world.item.equipment.ArmorType.class);
        defense.put(net.minecraft.world.item.equipment.ArmorType.HELMET, helmetDefense);
        defense.put(net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, chestDefense);
        defense.put(net.minecraft.world.item.equipment.ArmorType.LEGGINGS, leggingsDefense);
        defense.put(net.minecraft.world.item.equipment.ArmorType.BOOTS, bootsDefense);
        defense.put(net.minecraft.world.item.equipment.ArmorType.BODY, chestDefense);
        return new net.minecraft.world.item.equipment.ArmorMaterial(durability, defense, enchantmentValue,
                SoundEvents.ARMOR_EQUIP_GENERIC, toughness, knockbackResistance, repairTag(),
                net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID,
                        Ids.of(namespace, name)));
    }
    //?} else if >=1.21.1 {
    /*private net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> createNative() {
        Map<net.minecraft.world.item.ArmorItem.Type, Integer> defense =
                new EnumMap<>(net.minecraft.world.item.ArmorItem.Type.class);
        defense.put(net.minecraft.world.item.ArmorItem.Type.HELMET, helmetDefense);
        defense.put(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, chestDefense);
        defense.put(net.minecraft.world.item.ArmorItem.Type.LEGGINGS, leggingsDefense);
        defense.put(net.minecraft.world.item.ArmorItem.Type.BOOTS, bootsDefense);
        net.minecraft.world.item.ArmorMaterial material = new net.minecraft.world.item.ArmorMaterial(
                defense, enchantmentValue, SoundEvents.ARMOR_EQUIP_GENERIC, () ->
                net.minecraft.world.item.crafting.Ingredient.of(repairTag()), List.of(new net.minecraft.world.item.ArmorMaterial.Layer(
                        Ids.of(namespace, name))),
                toughness, knockbackResistance);
        return net.minecraft.core.Holder.direct(material);
    }
    *///?} else {
    /*private net.minecraft.world.item.ArmorMaterial createNative() {
        return new net.minecraft.world.item.ArmorMaterial() {
            @Override public int getDurabilityForType(net.minecraft.world.item.ArmorItem.Type type) {
                return durability * switch (type) {
                    case BOOTS -> 13; case LEGGINGS -> 15; case CHESTPLATE -> 16; case HELMET -> 11;
                };
            }
            @Override public int getDefenseForType(net.minecraft.world.item.ArmorItem.Type type) {
                return switch (type) {
                    case HELMET -> helmetDefense; case CHESTPLATE -> chestDefense;
                    case LEGGINGS -> leggingsDefense; case BOOTS -> bootsDefense;
                };
            }
            @Override public int getEnchantmentValue() { return enchantmentValue; }
            @Override public net.minecraft.sounds.SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_GENERIC; }
            @Override public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
                return net.minecraft.world.item.crafting.Ingredient.of(repairTag());
            }
            @Override public String getName() { return namespace + ":" + name; }
            @Override public float getToughness() { return toughness; }
            @Override public float getKnockbackResistance() { return knockbackResistance; }
        };
    }
    *///?}
}
