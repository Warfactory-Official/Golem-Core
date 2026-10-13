package dev.golem.core.world;

import java.util.List;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tool-material arithmetic shared by {@link CompatToolItem}, {@link CompatSwordItem} and
 * {@link CompatHoeItem}.
 *
 * <p>The three supported eras spell a tool's material three ways (see {@link ToolTier}); everything a
 * consumer asks of it (how fast does it dig this block, may it drop it) is answered here from the
 * legacy harvest level, so the answer is the same on every target.
 */
public final class Tools {

    private Tools() {}

    /** The tier's speed if {@code state} is in any of the {@code mineable} tags, otherwise 1. */
    public static float destroySpeed(ToolTier tier, List<TagKey<Block>> mineable, BlockState state) {
        for (TagKey<Block> tag : mineable) {
            if (state.is(tag)) return tier.speed();
        }
        return 1.0F;
    }

    /** Whether a tool of {@code tier} effective against {@code mineable} yields drops from {@code state}. */
    public static boolean correctForDrops(ToolTier tier, List<TagKey<Block>> mineable, BlockState state) {
        for (TagKey<Block> tag : mineable) {
            if (state.is(tag)) return meetsLevel(state, tier.level());
        }
        return false;
    }

    /** Whether a legacy harvest level is high enough for the block's tool requirement. */
    public static boolean meetsLevel(BlockState state, int level) {
        //? if >=1.21.1 {
        return !state.is(incorrectFor(level));
        //?} else {
        /*if (level < 3 && state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return false;
        if (level < 2 && state.is(BlockTags.NEEDS_IRON_TOOL)) return false;
        return level >= 1 || !state.is(BlockTags.NEEDS_STONE_TOOL);
        *///?}
    }

    //? if >=1.21.1 {
    /** The vanilla "incorrect for drops" tag matching a legacy harvest level. */
    public static TagKey<Block> incorrectFor(int level) {
        if (level <= 0) return BlockTags.INCORRECT_FOR_WOODEN_TOOL;
        if (level == 1) return BlockTags.INCORRECT_FOR_STONE_TOOL;
        if (level == 2) return BlockTags.INCORRECT_FOR_IRON_TOOL;
        if (level == 3) return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
        return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
    }
    //?}

    /** The vanilla attack speed of a pickaxe (-2.8), used for modern attack cooldown. */
    public static final float PICKAXE_SPEED = -2.8F;
    /** The vanilla attack speed of a shovel. */
    public static final float SHOVEL_SPEED = -3.0F;
    /** The vanilla attack speed of a sword. */
    public static final float SWORD_SPEED = -2.4F;

    /** Vanilla axe attack speed for a legacy level: wood/stone -3.2, iron -3.1, diamond and up -3.0. */
    public static float axeSpeed(int level) {
        if (level <= 1) return -3.2F;
        if (level == 2) return -3.1F;
        return -3.0F;
    }

    /** Vanilla hoe attack speed for a legacy level: wood -3, stone -2, iron -1, diamond and up 0. */
    public static float hoeSpeed(int level) {
        if (level <= 0) return -3.0F;
        if (level == 1) return -2.0F;
        if (level == 2) return -1.0F;
        return 0.0F;
    }

    //? if >=26.1 {
    /** A 26.1 tool material for a tier; enchantability is clamped because 26.1 rejects zero. */
    public static net.minecraft.world.item.ToolMaterial material(ToolTier tier) {
        return new net.minecraft.world.item.ToolMaterial(incorrectFor(tier.level()), tier.uses(), tier.speed(),
                tier.attackDamageBonus(), Math.max(1, tier.enchantability()), repairTag(tier));
    }
    //?} else {
    /*public static net.minecraft.world.item.Tier vanillaTier(ToolTier tier) {
        return new net.minecraft.world.item.Tier() {
            @Override public int getUses() { return tier.uses(); }
            @Override public float getSpeed() { return tier.speed(); }
            @Override public float getAttackDamageBonus() { return tier.attackDamageBonus(); }
            //? if >=1.21.1 {
            @Override public TagKey<Block> getIncorrectBlocksForDrops() { return incorrectFor(tier.level()); }
            //?} else {
            /^@Override public int getLevel() { return tier.level(); }^/
            //?}
            @Override public int getEnchantmentValue() { return tier.enchantability(); }
            @Override public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
                return tier.repairItems() == null ? net.minecraft.world.item.crafting.Ingredient.EMPTY
                        : net.minecraft.world.item.crafting.Ingredient.of(tier.repairItems());
            }
        };
    }
    *///?}

    static TagKey<Item> repairTag(ToolTier tier) {
        return tier.repairItems() != null ? tier.repairItems()
                : TagKey.create(net.minecraft.core.registries.Registries.ITEM, dev.golem.core.Ids.of("golemcore", "no_repair"));
    }

    /**
     * Makes a tool with no durability truly unbreakable where the base class would otherwise give it a
     * maximum damage of zero (26.1 and 1.21.1 both write {@code durability(uses)} unconditionally, and
     * a zero maximum reads as "breaks on the first use"). The unbreakable marker is hidden, since the
     * legacy tools never said so.
     */
    static Item.Properties unbreakableIfWorn(Item.Properties properties, ToolTier tier) {
        if (tier.uses() > 0) return properties;
        //? if >=26.1 {
        return properties.component(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)
                .component(net.minecraft.core.component.DataComponents.TOOLTIP_DISPLAY,
                        net.minecraft.world.item.component.TooltipDisplay.DEFAULT
                                .withHidden(net.minecraft.core.component.DataComponents.UNBREAKABLE, true));
        //?} else if >=1.21.1 {
        /*return properties.component(net.minecraft.core.component.DataComponents.UNBREAKABLE,
                new net.minecraft.world.item.component.Unbreakable(false));
        *///?} else {
        /*return properties;
        *///?}
    }

    //? if >=1.21.1 {
    /** Main-hand attack damage (absolute modifier), attack speed and a base-multiplied movement modifier. */
    static net.minecraft.world.item.component.ItemAttributeModifiers attributes(float attackDamage, float attackSpeed,
                                                                             double movement) {
        var builder = net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                dev.golem.core.Ids.of("minecraft", "base_attack_damage"), attackDamage,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                dev.golem.core.Ids.of("minecraft", "base_attack_speed"), attackSpeed,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND);
        if (movement != 0.0D) {
            builder.add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                    new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            dev.golem.core.Ids.of("golemcore", "tool_movement"), movement,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                    net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND);
        }
        return builder.build();
    }
    //?} else {
    /*static com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            net.minecraft.world.entity.ai.attributes.AttributeModifier> legacyAttributes(
            java.util.UUID damageId, java.util.UUID speedId, float attackDamage, float attackSpeed, double movement) {
        com.google.common.collect.ImmutableMultimap.Builder<net.minecraft.world.entity.ai.attributes.Attribute,
                net.minecraft.world.entity.ai.attributes.AttributeModifier> builder =
                com.google.common.collect.ImmutableMultimap.builder();
        builder.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(damageId, "Tool modifier", attackDamage,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        builder.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(speedId, "Tool modifier", attackSpeed,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        if (movement != 0.0D) {
            builder.put(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                    new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            java.util.UUID.nameUUIDFromBytes("golemcore:tool_movement".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            "Tool modifier", movement,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
        }
        return builder.build();
    }
    *///?}

    /** Damages a held tool by {@code amount}, breaking it with the vanilla break event when it wears out. */
    public static void hurt(ItemStack stack, int amount, net.minecraft.world.entity.LivingEntity user) {
        if (amount <= 0 || stack.isEmpty()) return;
        //? if >=1.21.1 {
        stack.hurtAndBreak(amount, user, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        //?} else {
        /*stack.hurtAndBreak(amount, user, e -> e.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        *///?}
    }
}
