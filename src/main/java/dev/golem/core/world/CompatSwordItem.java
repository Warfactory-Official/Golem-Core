package dev.golem.core.world;

/**
 * A vanilla sword item with one constructor across the supported tool-material APIs.
 *
 * <p>The modern 26.1 API stores sword attributes on {@code Item.Properties}; 1.21.1 still uses
 * {@code SwordItem} but moved its constructor and Tier harvest method; 1.20.1 retains the older
 * {@code SwordItem(Tier, int, float, Properties)} spelling. The seam keeps those API details in Core
 * while HBM supplies the original steel material values once.
 */
public class CompatSwordItem extends
        //? if >=26.1 {
        net.minecraft.world.item.Item
        //?} else {
        /*net.minecraft.world.item.SwordItem
        *///?}
{
    public CompatSwordItem(net.minecraft.world.item.Item.Properties properties,
                           int durability, float attackDamage, float attackSpeed,
                           net.minecraft.tags.TagKey<net.minecraft.world.item.Item> repairTag) {
        //? if >=26.1 {
        super(properties.sword(steelMaterial(durability, repairTag), attackDamage, attackSpeed)
                .durability(durability)
                .repairable(repairTag));
        //?} else if >=1.21.1 {
        /*super(steelTier(durability, repairTag),
                properties.durability(durability).attributes(
                        net.minecraft.world.item.SwordItem.createAttributes(
                                steelTier(durability, repairTag), (int) attackDamage, attackSpeed)));
        *///?} else {
        /*super(steelTier(durability, repairTag), (int) attackDamage, attackSpeed,
                properties.durability(durability));
        *///?}
    }

    //? if >=26.1 {
    /** The original HBM steel tool material: level 3, 750 uses, speed 8, damage bonus 2, ench 10. */
    private static net.minecraft.world.item.ToolMaterial steelMaterial(
            int durability, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> repairTag) {
        return new net.minecraft.world.item.ToolMaterial(
                net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
                durability, 8.0F, 2.0F, 10, repairTag);
    }
    //?} else {
    /*private static net.minecraft.world.item.Tier steelTier(
            int durability, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> repairTag) {
        return new net.minecraft.world.item.Tier() {
            @Override public int getUses() { return durability; }
            @Override public float getSpeed() { return 8.0F; }
            @Override public float getAttackDamageBonus() { return 2.0F; }
            //? if >=1.21.1 {
            @Override public net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>
                    getIncorrectBlocksForDrops() {
                return net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
            }
            //?} else {
            /^@Override public int getLevel() { return 3; }^/
            //?}
            @Override public int getEnchantmentValue() { return 10; }
            @Override public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
                return net.minecraft.world.item.crafting.Ingredient.of(repairTag);
            }
        };
    }
    *///?}
}
