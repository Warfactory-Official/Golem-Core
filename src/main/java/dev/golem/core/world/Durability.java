package dev.golem.core.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Legacy {@code ItemStack.damageItem(amount, entity)} on a worn or held item in any slot: vanilla's own durability
 * path, so Unbreaking, creative players and the break animation behave as vanilla does. The callback changed from a
 * {@code Consumer} (1.20.1) to an {@code EquipmentSlot} (1.20.5+), so the spelling lives here.
 */
public final class Durability {

    private Durability() {}

    /** Wears {@code amount} durability off the item {@code entity} has in {@code slot}, breaking it when it wears out. */
    public static void hurt(ItemStack stack, int amount, LivingEntity entity, EquipmentSlot slot) {
        if (amount <= 0 || stack.isEmpty()) return;
        //? if >=1.21.1 {
        stack.hurtAndBreak(amount, entity, slot);
        //?} else {
        /*stack.hurtAndBreak(amount, entity, e -> e.broadcastBreakEvent(slot));
        *///?}
    }

    /** Applies Unbreaking at {@code level} (for tests and synthesized stacks). */
    public static void enchantUnbreaking(ServerLevel world, ItemStack stack, int level) {
        //? if >=1.21.1 {
        stack.enchant(world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.UNBREAKING), level);
        //?} else {
        /*stack.enchant(Enchantments.UNBREAKING, level);
        *///?}
    }
}
