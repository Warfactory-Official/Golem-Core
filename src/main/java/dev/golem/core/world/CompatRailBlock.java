package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A flexible (turnable) rail with its own cart speed cap and a per-tick hook for every cart passing over it,
 * in the Forge spelling: {@code getRailMaxSpeed} and {@code onMinecartPass}.
 *
 * <p>Forge 1.20.1 and NeoForge 1.21.1 route every rail-riding cart through those two extension methods,
 * so there these declarations override the loader's defaults. NeoForge 26.1 declares them on
 * {@code IBaseRailBlockExtension} but does not call either one. Its minecart patches were dropped with the
 * 1.21.2 minecart rewrite, and Fabric never had them. On those nodes {@code MixinMinecartRails} calls them at
 * the equivalent points: the speed cap where the cart asks for its maximum speed, and the pass once per server
 * tick for the rail the cart started the tick on. The rail's position is resolved exactly as the loader's
 * {@code getCurrentRailPosition} did (the cart's block, or the rail one below it).
 *
 * <p>The vanilla default speed is the vanilla cart's own: 0.4 blocks/tick, 0.2 in water.
 */
public class CompatRailBlock extends RailBlock {

    /** Forge's {@code getCurrentCartSpeedCapOnRail}: no rail may speed a cart past this. */
    public static final float CART_SPEED_CAP = 1.2F;

    public CompatRailBlock(Properties properties) {
        super(properties);
    }

    /** The cart's speed cap on this rail, in blocks per tick. */
    public float getRailMaxSpeed(BlockState state, Level level, BlockPos pos, AbstractMinecart cart) {
        return cart.isInWater() ? 0.2F : 0.4F;
    }

    /** Called once per server tick for a cart riding this rail. */
    public void onMinecartPass(BlockState state, Level level, BlockPos pos, AbstractMinecart cart) {}

    /** The rail block a cart is riding, the loader's {@code getCurrentRailPosition}. */
    public static BlockPos railPosition(AbstractMinecart cart) {
        int x = Mth.floor(cart.getX());
        int y = Mth.floor(cart.getY());
        int z = Mth.floor(cart.getZ());
        if (cart.level().getBlockState(new BlockPos(x, y - 1, z)).is(BlockTags.RAILS)) y--;
        return new BlockPos(x, y, z);
    }
}
