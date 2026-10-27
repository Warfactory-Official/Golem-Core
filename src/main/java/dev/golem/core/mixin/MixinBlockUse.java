package dev.golem.core.mixin;

import dev.golem.core.world.BlockUses;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla has no accepted-use callback; loader interaction events run before the result.
 * Widening cannot add notification, so publish only after native use consumes the action.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinBlockUse {
    //? if >=1.20.5 {
    @Inject(method = "useWithoutItem", at = @At("RETURN"))
    private void golem$acceptedBlockUse(Level level, Player player, BlockHitResult hit,
                                       CallbackInfoReturnable<InteractionResult> callback) {
    //?} else {
    /*@Inject(method = "use", at = @At("RETURN"))
    private void golem$acceptedBlockUse(Level level, Player player, InteractionHand hand, BlockHitResult hit,
                                       CallbackInfoReturnable<InteractionResult> callback) {
    *///?}
        if (callback.getReturnValue().consumesAction() && !BlockUses.AFTER.isEmpty()) {
            BlockUses.accepted(level, hit.getBlockPos(), (BlockState) (Object) this, player);
        }
    }
}
