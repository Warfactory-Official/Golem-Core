package dev.golem.core.autocraft;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/** One autocrafting job: ingredients already taken from storage, and its output. Fluids in mB on every loader. */
public record CraftingPlan(List<ItemStack> items, List<FluidAmount> fluids, @Nullable ItemStack primaryItem,
                           @Nullable Fluid primaryFluid) {

    public record FluidAmount(Fluid fluid, int mB) {
    }
}
