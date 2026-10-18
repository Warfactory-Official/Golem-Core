package dev.golem.core.mixin;

//? if >=26.1 {
import dev.golem.core.player.SlotSync;
import net.minecraft.client.gui.screens.inventory.CreativeInventoryListener;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The creative listener does not answer a server slot push with a creative slot write ({@link SlotSync}); it still
 * sends every change the player makes. The push is marked by {@link MixinCreativeEchoPush}.
 *
 * <p>Why a mixin: (1) vanilla has no way to tell the listener a change came from the server; (2) neither loader
 * wraps it; (3) widening does not help, the decision is inside {@code slotChanged}. One cancellable check at its
 * head. Empty and not listed before 26.1, where nothing marks a push.
 */
@Mixin(CreativeInventoryListener.class)
public abstract class MixinCreativeEchoListener {

    @Inject(method = "slotChanged", at = @At("HEAD"), cancellable = true)
    private void golem$skipServerPush(AbstractContainerMenu container, int slotIndex, ItemStack itemStack,
                                      CallbackInfo ci) {
        if (SlotSync.echoSuppressed) ci.cancel();
    }
}
//?} else {
/*import net.minecraft.client.gui.screens.inventory.CreativeInventoryListener;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CreativeInventoryListener.class)
public abstract class MixinCreativeEchoListener {}
*///?}
