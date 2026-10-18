package dev.golem.core.mixin;

import dev.golem.core.player.SlotSync;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A creative client's slot write is always confirmed back to it ({@link SlotSync}).
 *
 * <p>26.1 records the written stack as the client's copy, so a slot update the server sent a moment before the write
 * (the stack changing every tick) lands on the client after it and is never corrected: a ghost, which the creative
 * client then writes back as a real item. Before 26.1 vanilla left the copy alone and sent the slot back whenever it
 * differed; forgetting it on every node sends it back always, which is that behaviour without the race.
 *
 * <p>Why a mixin: (1) vanilla has no hook in the creative slot handler; (2) neither loader raises an event for it;
 * (3) widening does not help, the copy is recorded inside the handler. One call before the handler's broadcast.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinSlotSyncCreative {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/InventoryMenu;broadcastChanges()V"))
    private void golem$confirmCreativeSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        //? if >=1.20.5 {
        SlotSync.forget(player.inventoryMenu, packet.slotNum());
        //?} else {
        /*SlotSync.forget(player.inventoryMenu, packet.getSlotNum());
        *///?}
    }
}
