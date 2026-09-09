package dev.golem.core.mixin;

//? if >=26.1 {
import dev.golem.core.render.WorldEffects;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla exposes no post-composition callback. Fabric END_MAIN and NeoForge's last render stage
// are before Fabulous composition; FrameGraphSetupEvent is before passes are added. Access widening
// cannot add that timing point. Older versions use loader events instead of this mixin.
@Mixin(LevelRenderer.class)
public class MixinWorldEffects {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void golem$beginWorldEffects(CallbackInfo ci) {
        WorldEffects.begin();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelTargetBundle;clear()V"))
    private void golem$finishWorldEffects(CallbackInfo ci) {
        WorldEffects.end();
    }
}
//?}
