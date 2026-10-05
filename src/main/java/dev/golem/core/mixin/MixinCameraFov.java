package dev.golem.core.mixin;

// The vanilla/Fabric API has no FOV event. This is the smallest hook at the value's return point;
// Forge-like loaders use their ViewportEvent backend in Fov instead. A MixinExtras @ModifyReturnValue rather than a
// cancellable @Inject at RETURN: the inject cancelled the method on every frame, and its early return could skip
// another mod's return hook on the same method (zoom mods: Zoomify, Ok Zoomer, ...). Return-value modifiers chain.
//? if fabric {

/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.golem.core.client.Fov;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=26.1 {
import net.minecraft.client.Camera;

@Mixin(Camera.class)
public class MixinCameraFov {

    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float golem$modifyFov(float fov, @Local(argsOnly = true) float partialTick) {
        return Fov.apply(fov, partialTick);
    }
}
//?} else {
/^import net.minecraft.client.renderer.GameRenderer;

@Mixin(GameRenderer.class)
public class MixinCameraFov {

    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private double golem$modifyFov(double fov, @Local(argsOnly = true) float partialTick,
            @Local(argsOnly = true) boolean useFovSetting) {
        return useFovSetting ? Fov.apply((float) fov, partialTick) : fov;
    }
}
^///?}
*///?}
