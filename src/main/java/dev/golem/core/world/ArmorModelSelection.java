package dev.golem.core.world;

import net.minecraft.world.entity.EquipmentSlot;

/**
 * An equipped item can use another armor model's geometry without changing its equipment slot or texture layer.
 */
public interface ArmorModelSelection {
    EquipmentSlot armorModelSlot();
}
