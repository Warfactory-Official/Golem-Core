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
 * Raises the frame limit so paced ticks are not starved: 26.1 caps unfocused and idle windows, older nodes the
 * player's option. No loader API changes either, and this config is stripped from release jars.
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
