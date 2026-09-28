package dev.golem.core.world;

import net.minecraft.world.entity.EquipmentSlot;

/**
 * A plain item that is also a melee weapon: it carries a main-hand attack-damage and attack-speed
 * modifier without being a sword or a digger (no cobweb speed, no mining rules, no sword enchantments).
 *
 * <p>1.21.1 and 26.1 describe the modifiers as the item's {@code attribute_modifiers} property; 1.20.1
 * asks the item for them per slot. Legacy content wrote the damage as an absolute "Weapon modifier",
 * which is what {@code attackDamage} is here.
 */
public class CompatWeaponItem extends CompatItem {

    //? if <1.21.1 {
    /*private final float legacyDamage;
    private final float legacySpeed;
    *///?}

    public CompatWeaponItem(Properties properties, float attackDamage, float attackSpeed) {
        //? if >=1.21.1 {
        super(properties.attributes(Tools.attributes(attackDamage, attackSpeed, 0.0D)));
        //?} else {
        /*super(properties);
        this.legacyDamage = attackDamage;
        this.legacySpeed = attackSpeed;
        *///?}
    }

    //? if <1.21.1 {
    /*@Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            net.minecraft.world.entity.ai.attributes.AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND
                ? Tools.legacyAttributes(BASE_ATTACK_DAMAGE_UUID, BASE_ATTACK_SPEED_UUID, legacyDamage, legacySpeed, 0.0D)
                : super.getDefaultAttributeModifiers(slot);
    }
    *///?}
}
