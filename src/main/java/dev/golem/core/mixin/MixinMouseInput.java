package dev.golem.core.mixin;

// Fabric exposes no cancellable world mouse-input event. Injecting at the tail preserves all key-mapping
// updates, then gives consumers time to drain conflicting vanilla clicks before Minecraft handles them.
//? if fabric {

/*import dev.golem.core.client.MouseInput;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouseInput {

    //? if >=26.1 {
    @Inject(method = "onButton", at = @At("TAIL"))
    private void golem$afterMouseButton(long window, net.minecraft.client.input.MouseButtonInfo button,
            int action, CallbackInfo callback) {
        MouseInput.fireButton(button.button(), action == org.lwjgl.glfw.GLFW.GLFW_PRESS);
    }
    //?} else {
    /^
    @Inject(method = "onPress", at = @At("TAIL"))
    private void golem$afterMouseButton(long window, int button, int action, int modifiers,
            CallbackInfo callback) {
        MouseInput.fireButton(button, action == org.lwjgl.glfw.GLFW.GLFW_PRESS);
    }
    ^/
    //?}
}
*///?}
