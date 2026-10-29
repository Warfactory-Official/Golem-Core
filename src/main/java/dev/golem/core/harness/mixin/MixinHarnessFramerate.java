package dev.golem.core.harness.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.golem.core.harness.HarnessPacing;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=26.1 {
import com.mojang.blaze3d.platform.FramerateLimitTracker;
//?}

/**
 * No loader API bypasses vanilla's inactivity frame cap; widening cannot change the computed limit.
 */
//? if >=26.1 {
@Mixin(FramerateLimitTracker.class)
//?} else {
/*@Mixin(Minecraft.class)
 *///?}
public abstract class MixinHarnessFramerate {

    @ModifyReturnValue(method = "getFramerateLimit", at = @At("RETURN"))
    private int golem$harnessFrameLimit(int original) {
        return HarnessPacing.frameLimit(original);
    }
}
