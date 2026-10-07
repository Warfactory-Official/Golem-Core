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
        if (!dev.golem.core.client.ShaderPacks.inUse()) WorldEffects.end();
    }

    // Under a shader pack, Iris runs its composite and final passes at this method's popMatrix (its own inject
    // there), writing the shaded frame into the main target and painting over anything drawn before. So world
    // effects end just after that call: over the finished image, with the world's depth still in the main depth
    // texture (Iris renders into it as depthtex0, without reverse-Z). The same point needs no other mixin: it is
    // the existing post-composition hook, moved later only while a pack is active. No-op once ended.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;", shift = At.Shift.AFTER))
    private void golem$finishWorldEffectsAfterShaderPack(CallbackInfo ci) {
        WorldEffects.end();
    }
}
//?}
