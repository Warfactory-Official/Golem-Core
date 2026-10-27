package dev.golem.core.harness.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla follows IDE mode and Fabric has no independent toggle; widening cannot preserve a final startup decision. */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlRenderPass", remap = false)
public abstract class MixinGlValidation {
    @ModifyExpressionValue(method = "<clinit>",
            at = @At(value = "FIELD", target = "Lnet/minecraft/SharedConstants;IS_RUNNING_IN_IDE:Z"), require = 1)
    private static boolean golem$validation(boolean ide) {
        return ide && !Boolean.getBoolean("golemcore.disableGlValidation");
    }
}
