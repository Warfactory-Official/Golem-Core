package dev.golem.core.autocraft;

import net.minecraft.world.item.ItemStack;

/** A machine an autocrafting network can hand whole jobs to, instead of inserting ingredients into its inventory. */
public interface CraftingMachine {

    boolean acceptsPlans();

    /** Takes every ingredient of {@code plan}, or none of them and returns false. */
    boolean push(CraftingPlan plan);

    ItemStack icon();
}
