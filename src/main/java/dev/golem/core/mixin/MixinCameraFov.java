package dev.golem.core.mixin;

// The vanilla/Fabric API has no FOV event. This is the smallest hook at the value's return point;
// Forge-like loaders use their ViewportEvent backend in Fov instead.
//? if fabric {

/*import dev.golem.core.client.Fov;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >=26.1 {
import net.minecraft.client.Camera;

@Mixin(Camera.class)
public class MixinCameraFov {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void golem$modifyFov(float partialTick, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(Fov.apply(callback.getReturnValue(), partialTick));
    }
}
//?} else {
/^import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

@Mixin(GameRenderer.class)
public class MixinCameraFov {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void golem$modifyFov(Camera camera, float partialTick, boolean useFovSetting,
            CallbackInfoReturnable<Double> callback) {
        if (useFovSetting) {
            callback.setReturnValue((double) Fov.apply(callback.getReturnValue().floatValue(), partialTick));
        }
    }
}
^///?}
*///?}
