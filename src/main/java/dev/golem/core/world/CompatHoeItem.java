package dev.golem.core.world;

import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;

/**
 * A vanilla hoe (tilling, hoe-mineable blocks) built from a {@link ToolTier}.
 *
 * <p>All three eras keep {@code HoeItem}, with three constructors: 26.1 takes a {@code ToolMaterial}
 * and two baselines, 1.21.1 a {@code Tier} plus attributes on the properties, 1.20.1 a {@code Tier}
 * with an integer damage baseline. {@code attackDamage} here is the absolute main-hand modifier.
 */
public class CompatHoeItem extends HoeItem {

    protected final ToolTier toolTier;

    public CompatHoeItem(Item.Properties properties, ToolTier tier, float attackDamage, float attackSpeed) {
        //? if >=26.1 {
        super(Tools.material(tier), attackDamage - tier.attackDamageBonus(), attackSpeed,
                Tools.unbreakableIfWorn(properties, tier));
        //?} else if >=1.21.1 {
        /*super(Tools.vanillaTier(tier), Tools.unbreakableIfWorn(properties, tier).attributes(
                HoeItem.createAttributes(Tools.vanillaTier(tier), attackDamage - tier.attackDamageBonus(), attackSpeed)));
        *///?} else {
        /*super(Tools.vanillaTier(tier), Math.round(attackDamage - tier.attackDamageBonus()), attackSpeed, properties);
        *///?}
        this.toolTier = tier;
    }

    public ToolTier toolTier() {
        return toolTier;
    }
}
