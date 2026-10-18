package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Whether the loader's right-click-block event lets a player use a block (a protected area refuses a menu's control
 * packet). Fabric denies with {@code FAIL}; {@link ItemFirstUse} handlers skip the probe.
 */
public final class Interactions {

    private static final ThreadLocal<Boolean> PROBING = ThreadLocal.withInitial(() -> false);

    private Interactions() {}

    public static boolean allowsUse(Player player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        PROBING.set(true);
        try {
            //? if fabric {
            /*return net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker()
                    .interact(player, player.level(), InteractionHand.MAIN_HAND, hit) != net.minecraft.world.InteractionResult.FAIL;
            *///?} elif neoforge {
            return !net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit).isCanceled();
            //?} elif forge {
            /*return !net.minecraftforge.common.ForgeHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit).isCanceled();
            *///?}
        } finally {
            PROBING.set(false);
        }
    }

    static boolean probing() {
        return PROBING.get();
    }
}
