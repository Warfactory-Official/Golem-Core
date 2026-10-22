package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightShaders;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
abstract class SodiumShaderLoaderMixin {
    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
    private static void gemrender$patch(ResourceLocation name, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(LightShaders.sodium(name, cir.getReturnValue()));
    }
}
