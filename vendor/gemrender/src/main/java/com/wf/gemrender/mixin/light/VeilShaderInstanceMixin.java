package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightShaders;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShaderInstance.class, priority = 1100)
abstract class VeilShaderInstanceMixin {
    @ModifyVariable(method = "veil$recompile(ZLjava/lang/String;I)V", at = @At("HEAD"), argsOnly = true,
            require = 0, remap = false)
    private String gemrender$patch(String source, boolean vertex) {
        return LightShaders.vanilla(((ShaderInstance) (Object) this).getName(), vertex, source);
    }

    @Inject(method = "veil$applyCompile()Z", at = @At("RETURN"), require = 0, remap = false)
    private void gemrender$bindLights(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            LightShaders.bindProgram(((ShaderInstance) (Object) this).getId());
        }
    }
}
