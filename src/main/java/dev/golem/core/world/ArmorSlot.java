package dev.golem.core.world;

/**
 * Which piece of armour an item is.
 *
 * <p>Both eras have this enum, under different names and packages: {@code ArmorType} on 26.1,
 * {@code ArmorItem.Type} before it, and each is woven into its own armour API. Consumers name the
 * slot through this neutral enum so that only the small amount of code actually constructing armour
 * needs a version guard, rather than every declaration site.
 *
 * <p>26.1's extra {@code BODY} value (for wolf armour and similar) is absent: HBM does
 * not use it, and including it would force every consumer to handle a case one era cannot express.
 */
public enum ArmorSlot {
    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS
}
