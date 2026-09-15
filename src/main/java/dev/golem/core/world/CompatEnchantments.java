package dev.golem.core.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Version-neutral enchantment operations used by common gameplay code. */
public final class CompatEnchantments {

    private CompatEnchantments() {}

    /** Applies vanilla's blast-protection dampener to explosion knockback. */
    public static double blastKnockback(ServerLevel level, LivingEntity entity, double knockback) {
        //? if >=1.21.1 {
        int protection = EnchantmentHelper.getEnchantmentLevel(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.BLAST_PROTECTION), entity);
        return knockback * net.minecraft.util.Mth.clamp(1D - protection * 0.15D, 0D, 1D);
        //?} else {
        /*return net.minecraft.world.item.enchantment.ProtectionEnchantment
                .getExplosionKnockbackAfterDampener(entity, knockback);
        *///?}
    }

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

    /** Adjusts only Fortune on a stack while retaining every other enchantment. */
    public static void adjustFortune(ServerLevel level, ItemStack stack, int delta) {
        //? if >=1.21.1 {
        var fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        int result = EnchantmentHelper.getItemEnchantmentLevel(fortune, stack) + delta;
        stack.update(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY, old -> {
                    var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(old);
                    if (result > 0) mutable.set(fortune, result);
                    else mutable.removeIf(holder -> holder.is(Enchantments.FORTUNE));
                    return mutable.toImmutable();
                });
        //?} else {
        /*net.minecraft.world.item.enchantment.Enchantment fortune = Enchantments.BLOCK_FORTUNE;
        java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> enchantments =
                EnchantmentHelper.getEnchantments(stack);
        int result = enchantments.getOrDefault(fortune, 0) + delta;
        if (result > 0) enchantments.put(fortune, result);
        else enchantments.remove(fortune);
        EnchantmentHelper.setEnchantments(enchantments, stack);
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
