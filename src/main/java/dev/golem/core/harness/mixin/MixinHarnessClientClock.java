package dev.golem.core.harness.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.golem.core.harness.HarnessPacing;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if <1.21 {
/*import net.minecraft.client.Timer;
 *///?}

/**
 * Vanilla and loaders expose no client-clock setter; access widening cannot replace its timing calculation.
 */
//? if >=1.21 {
@Mixin(Minecraft.class)
//?} else {
/*@Mixin(Timer.class)
 *///?}
public abstract class MixinHarnessClientClock {

    //? if >=1.21 {
    @ModifyReturnValue(method = "getTickTargetMillis", at = @At("RETURN"))
    //?} else {
    /*@ModifyExpressionValue(method = "advanceTime", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Timer;msPerTick:F"))
    *///?}
    private float golem$harnessClientMillis(float original) {
        return HarnessPacing.clientMillis(original);
    }
}
