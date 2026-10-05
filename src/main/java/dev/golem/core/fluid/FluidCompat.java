package dev.golem.core.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Reads whatever the loader's standard fluid capability answers, whoever implemented it: another mod's tank,
 * or one of ours as {@link FluidExposure} published it. The answer is a {@link FluidView} in mB.
 */
public final class FluidCompat {

    private FluidCompat() {}

    /** The fluid handler at {@code pos} on {@code side}, or null if there is none. */
    public static @Nullable FluidView find(Level level, BlockPos pos, @Nullable Direction side) {
        //? if neoforge && >=26.1 {
        return NeoTransferFluids.find(level, pos, side);
        //?} elif neoforge {
        /*return NeoLegacyFluids.find(level, pos, side);
        *///?} elif forge {
        /*return ForgeFluids.find(level, pos, side);
        *///?} else {
        /*return FabricTransferFluids.find(level, pos, side);
        *///?}
    }

    /**
     * The item fluid handler of one item of {@code stack}, on a private copy: moves through the view change the
     * copy, read back with {@link FluidView#container()}, never {@code stack} itself.
     */
    public static @Nullable FluidView findItem(ItemStack stack) {
        //? if neoforge && >=26.1 {
        return NeoTransferFluids.findItem(stack);
        //?} elif neoforge {
        /*return NeoLegacyFluids.findItem(stack);
        *///?} elif forge {
        /*return ForgeFluids.findItem(stack);
        *///?} else {
        /*return FabricTransferFluids.findItem(stack);
        *///?}
    }
}
