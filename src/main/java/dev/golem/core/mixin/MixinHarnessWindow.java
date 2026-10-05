package dev.golem.core.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Harness: a client under test must never take keyboard focus from the desktop. GLFW gives a new
 * window focus at creation and again when shown unless told otherwise, so the hints are set after
 * vanilla's own defaults and before the window exists. A no-op unless the harness port is set.
 */
@Mixin(Window.class)
public class MixinHarnessWindow {

    /**
     * Iconified windows report a 0x0 window size, and vanilla divides the cursor position by it for
     * every screen hit-test, so a minimized client cannot hover or click anything. The framebuffer keeps
     * its size (resize callbacks ignore 0x0), so answer with it while the window is minimized.
     */
    @Inject(method = "getScreenWidth", at = @At("RETURN"), cancellable = true)
    private void golem$harnessScreenWidth(CallbackInfoReturnable<Integer> callback) {
        if (callback.getReturnValueI() == 0 && dev.golem.core.harness.HarnessWindowHints.blockMouseGrab()) {
            callback.setReturnValue(((Window) (Object) this).getWidth());
        }
    }

    @Inject(method = "getScreenHeight", at = @At("RETURN"), cancellable = true)
    private void golem$harnessScreenHeight(CallbackInfoReturnable<Integer> callback) {
        if (callback.getReturnValueI() == 0 && dev.golem.core.harness.HarnessWindowHints.blockMouseGrab()) {
            callback.setReturnValue(((Window) (Object) this).getHeight());
        }
    }

    //? if >=26.1 {
    @Inject(method = "createGlfwWindow", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private static void golem$harnessWindowHints(CallbackInfoReturnable<Long> callback) {
        dev.golem.core.harness.HarnessWindowHints.apply();
    }
    //?} else {
    /*// A call wrapper rather than an @Inject: Forge 1.20.1 ships Mixin 0.8.5, which refuses @Inject at a
    // non-RETURN point inside a constructor, while wrapping a call is legal there on every version. A MixinExtras
    // @WrapOperation, not a @Redirect, because this class ships to players and window mods hook the same call.
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V", remap = false))
    private void golem$harnessWindowHints(com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call();
        dev.golem.core.harness.HarnessWindowHints.apply();
    }
    *///?}
}
