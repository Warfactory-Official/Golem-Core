package dev.golem.core.mixin;

//? if >=26.1 {
import dev.golem.core.player.SlotSync;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the broadcast 26.1 runs after applying a server slot push while a creative screen is open, so
 * {@link MixinCreativeEchoListener} does not send the pushed stack back ({@link SlotSync}).
 *
 * <p>The broadcast only exists from 26.1 (1.21.1 and 1.20.1 apply the push without it), so this class is empty and
 * not listed on the older nodes.
 *
 * <p>Why a mixin: (1) vanilla has no hook around the push; (2) neither loader raises an event for applying a slot
 * packet; (3) widening does not help, the echo is the listener's ordinary reaction to that broadcast. Two calls,
 * just before and just after the one broadcast; the push itself is untouched.
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinCreativeEchoPush {

    @Inject(method = "handleContainerSetSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/InventoryMenu;broadcastChanges()V"))
    private void golem$beginPush(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        SlotSync.echoSuppressed = true;
    }

    @Inject(method = "handleContainerSetSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/InventoryMenu;broadcastChanges()V", shift = At.Shift.AFTER))
    private void golem$endPush(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        SlotSync.echoSuppressed = false;
    }
}
//?} else {
/*import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientPacketListener.class)
public abstract class MixinCreativeEchoPush {}
*///?}
