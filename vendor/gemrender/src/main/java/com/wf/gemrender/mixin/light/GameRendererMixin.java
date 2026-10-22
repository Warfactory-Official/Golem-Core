package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightFrame;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lights end after the hand: hand pose stack starts with the inverse view rotation => camera-relative world. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void gemrender$endLights(CallbackInfo ci) {
        LightFrame.getInstance()
                .end();
    }
}
