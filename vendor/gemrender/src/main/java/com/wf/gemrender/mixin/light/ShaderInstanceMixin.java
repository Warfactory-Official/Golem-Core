package com.wf.gemrender.mixin.light;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.wf.gemrender.light.LightShaders;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderInstance.class)
abstract class ShaderInstanceMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
            at = @At("TAIL"))
    private void gemrender$bindLights(ResourceProvider provider, ResourceLocation location, VertexFormat format,
                                      CallbackInfo ci) {
        LightShaders.bindProgram(((ShaderInstance) (Object) this).getId());
    }
}
