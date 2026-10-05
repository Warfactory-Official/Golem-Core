package dev.golem.core.fluid;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * A loader-agnostic view over whatever fluid handler a block face or an item answers with, in mB, whoever
 * implemented it. Read through {@link FluidCompat}; mostly what a test or a probe wants, and what a mod's
 * own code would use to reach a foreign tank.
 *
 * <p>Fabric amounts are converted from droplets ({@link FluidUnits}) and floored to whole mB. A move is one
 * committed transaction on the transactional loaders, or one execute call on the simulate-flag ones.
 */
public interface FluidView {

    /** The native handler, so one block answering on several faces can be counted once. */
    Object handlerIdentity();

    int tanks();

    /** {@code Fluids.EMPTY} for an empty tank. */
    Fluid fluid(int tank);

    long amount(int tank);

    long capacity(int tank);

    /** @return mB accepted, between 0 and {@code mb} */
    long insert(Fluid fluid, long mb, boolean simulate);

    /** @return mB given, between 0 and {@code mb} */
    long extract(Fluid fluid, long mb, boolean simulate);

    /** For an item view, the single item as the moves so far left it; {@link ItemStack#EMPTY} otherwise. */
    default ItemStack container() {
        return ItemStack.EMPTY;
    }
}
