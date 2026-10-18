package dev.golem.core.mixin;

import dev.golem.core.player.SlotSync;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=26.1 {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/**
 * After the player drops the selected stack, the slot is sent again instead of assumed ({@link SlotSync}).
 *
 * <p>{@code ServerPlayer.drop(boolean)} records the post-drop stack as the client's copy, trusting the client's own
 * prediction. A slot update sent a moment before the drop then lands after that prediction and puts the stack back
 * on the client, where it stays: a ghost item. Only stacks that change every tick hit this, which no vanilla item does.
 *
 * <p>Why a mixin: (1) vanilla has no hook after the drop; (2) NeoForge/Forge raise {@code ItemTossEvent} after the
 * assumption is recorded, but Fabric API has no drop event, and one injection for all loaders is smaller than an
 * event on two and a mixin on the third; (3) widening does not help, the assumption is made inside the method. One
 * call at the method's returns, no change to what it does.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinSlotSyncDrop {

    //? if >=26.1 {
    @Inject(method = "drop(Z)V", at = @At("RETURN"))
    private void golem$resendDroppedSlot(boolean all, CallbackInfo ci) {
        SlotSync.forgetSelected((ServerPlayer) (Object) this);
    }
    //?} else {
    /*@Inject(method = "drop(Z)Z", at = @At("RETURN"))
    private void golem$resendDroppedSlot(boolean all, CallbackInfoReturnable<Boolean> cir) {
        SlotSync.forgetSelected((ServerPlayer) (Object) this);
    }
    *///?}
}
