package dev.golem.core.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Version-neutral enchantment operations used by common gameplay code. */
public final class CompatEnchantments {

    private CompatEnchantments() {}

    /** The Fortune level on the loot context's tool, or zero when it has none. */
    public static int fortune(LootParams.Builder params) {
        //? if >=26.1 {
        net.minecraft.world.item.ItemInstance tool = params.getOptionalParameter(LootContextParams.TOOL);
        if (tool == null || tool.count() <= 0) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(
                params.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.FORTUNE), tool);
        //?} else if >=1.21.1 {
        /*ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        if (tool == null || tool.getCount() <= 0) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(
                params.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.FORTUNE), tool);
        *///?} else {
        /*ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        if (tool == null || tool.getCount() <= 0) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
        *///?}
    }

    /** Whether the loot context's tool carries Silk Touch. */
    public static boolean silkTouch(LootParams.Builder params) {
        //? if >=26.1 {
        net.minecraft.world.item.ItemInstance tool = params.getOptionalParameter(LootContextParams.TOOL);
        if (tool == null || tool.count() <= 0) return false;
        return EnchantmentHelper.getItemEnchantmentLevel(
                params.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.SILK_TOUCH), tool) > 0;
        //?} else if >=1.21.1 {
        /*ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        if (tool == null || tool.getCount() <= 0) return false;
        return EnchantmentHelper.getItemEnchantmentLevel(
                params.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.SILK_TOUCH), tool) > 0;
        *///?} else {
        /*ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        if (tool == null || tool.getCount() <= 0) return false;
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0;
        *///?}
    }

    /** Applies Fortune to a tool for APIs which synthesize a harvesting stack. */
    public static void enchantFortune(ServerLevel level, ItemStack tool, int fortune) {
        if (fortune <= 0) return;
        //? if >=1.21.1 {
        tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FORTUNE), fortune);
        //?} else {
        /*tool.enchant(Enchantments.BLOCK_FORTUNE, fortune);
        *///?}
    }

    /** Whether an equipped stack may not be removed by a non-creative player. */
    public static boolean preventsArmorChange(ItemStack stack) {
        //? if >=1.21.1 {
        return EnchantmentHelper.has(stack,
                net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
        //?} else {
        /*return EnchantmentHelper.hasBindingCurse(stack);
        *///?}
    }
}
