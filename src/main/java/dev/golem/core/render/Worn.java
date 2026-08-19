package dev.golem.core.render;

import dev.golem.core.world.ArmorSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The four armour stacks an entity is wearing, as a render layer sees them.
 *
 * <p>This exists because the two eras hand a layer a different <em>subject</em>. From 26.1 a layer is
 * given a render state — a snapshot taken during extraction — and the worn stacks are already on it as
 * {@code headEquipment}/{@code chestEquipment}/{@code legsEquipment}/{@code feetEquipment}. Below 26.1
 * a layer is given the live entity and reads {@code getItemBySlot}. Both produce these four stacks, so
 * that is what the neutral layer contract passes along; nothing else about the subject is portable.
 *
 * <p>Entries are never null — an empty slot is {@link ItemStack#EMPTY}.
 *
 * <p>Client-only.
 */
public record Worn(ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {

    /** The stack in {@code slot}, or {@link ItemStack#EMPTY}. */
    public ItemStack get(ArmorSlot slot) {
        return switch (slot) {
            case HELMET -> head;
            case CHESTPLATE -> chest;
            case LEGGINGS -> legs;
            case BOOTS -> feet;
        };
    }

    /** Whether every slot is empty, which is the common case and worth skipping early. */
    public boolean isEmpty() {
        return head.isEmpty() && chest.isEmpty() && legs.isEmpty() && feet.isEmpty();
    }
}
