package dev.golem.core.harness.mixin;

import dev.golem.core.harness.HarnessWindowHints;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Harness: vanilla only grabs the cursor while the window is active, and a harness window never is;
 * this closes the remaining path (a stray focus event) so the desktop cursor is never captured.
 */
@Mixin(MouseHandler.class)
public class MixinHarnessMouseGrab {

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void golem$harnessNoGrab(CallbackInfo callback) {
        if (HarnessWindowHints.blockMouseGrab()) callback.cancel();
    }
}
