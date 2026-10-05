package dev.golem.core.fluid;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/**
 * A mod's fluid container items as a foreign fluid system sees them, in mB, one item at a time. Implemented by
 * the mod; {@link FluidExposure#exposeItems} builds each loader's item fluid handler on it.
 *
 * <p>Every method gets a single item ({@code one.getCount() == 1}) and must not mutate it: {@link #fill} and
 * {@link #drain} return what that one item becomes. The loader handler does the stack bookkeeping (exchanging
 * one item of a larger stack, committing or rolling back). A container states its own rules here, which
 * fluids it takes, at what amounts, and whether a draw must take everything, exactly as the mod's own
 * machines see them.
 */
public interface FluidContainer {

    /** Whether this contract answers for {@code stack} at all. */
    boolean handles(ItemStack stack);

    /** The fluid one item holds, {@code Fluids.EMPTY} if none. */
    Fluid fluid(ItemStack one);

    long amount(ItemStack one);

    /** What one item could hold of {@code fluid} ({@code Fluids.EMPTY}: of what it already holds); 0 refuses. */
    long capacity(ItemStack one, Fluid fluid);

    /** One item filled with up to {@code maxMb} of {@code fluid}, or null when it takes none. */
    @Nullable Moved fill(ItemStack one, Fluid fluid, long maxMb);

    /** One item drained of up to {@code maxMb} of {@code fluid}, or null when it gives none. */
    @Nullable Moved drain(ItemStack one, Fluid fluid, long maxMb);

    /**
     * The item after a move ({@link ItemStack#EMPTY} when the container is used up) and the mB that moved,
     * always positive.
     */
    record Moved(ItemStack result, long mb) {}
}
