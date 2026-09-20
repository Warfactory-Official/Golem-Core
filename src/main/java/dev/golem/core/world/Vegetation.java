package dev.golem.core.world;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "Is this block a plant?" across the {@code BushBlock} split.
 *
 * <p>Legacy {@code Material.plants} has no modern equivalent, and the class that stands in for it
 * changed shape: through 1.21.1 {@code BushBlock} is the base of every plant, and on 26.1 the base
 * is {@code VegetationBlock} with {@code BushBlock} a <b>sibling</b> of it rather than a parent.
 * Testing the sibling on the newer API silently excludes tall grass and ferns - the plants a
 * destruction pass is most likely to be aimed at - so both are tested where both exist.
 */
public final class Vegetation {

    private Vegetation() {}

    /** True for grass, ferns, flowers, saplings, crops and the rest of legacy {@code Material.plants}. */
    public static boolean isPlant(BlockState state) {
        Block block = state.getBlock();
        //? if >=26.1 {
        return block instanceof net.minecraft.world.level.block.VegetationBlock
                || block instanceof net.minecraft.world.level.block.BushBlock;
        //?} else {
        /*return block instanceof net.minecraft.world.level.block.BushBlock;
        *///?}
    }

    /** True for any leaf block. Stable across the range; here so callers have one vegetation import. */
    public static boolean isLeaves(BlockState state) {
        return state.getBlock() instanceof LeavesBlock;
    }
}
