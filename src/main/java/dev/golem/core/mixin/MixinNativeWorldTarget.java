package dev.golem.core.mixin;

//? if >=26.1 {
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.opengl.GlRenderPass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.golem.core.client.ShaderPacks;
import dev.golem.core.render.WorldEffects;
import dev.golem.core.render.WorldPipelines;

import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Vanilla and loader world-render events precede the encoder's target binding.
// Widening cannot insert a callback after pipeline setup for the native draw.
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class MixinNativeWorldTarget {
    @Inject(method = "trySetup", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/opengl/GlCommandEncoder;applyPipelineState(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V",
            shift = At.Shift.AFTER))
    private void golem$nativeWorldTarget(GlRenderPass pass, Collection<String> uniforms,
                                         CallbackInfoReturnable<Boolean> callback, @Local RenderPipeline pipeline) {
        // Only Core's world pipelines inside the level pass. GUI, private-target and shadow draws keep their targets.
        if (WorldPipelines.contains(pipeline) && WorldEffects.isCollecting()
                && RenderSystem.outputColorTextureOverride == null && RenderSystem.outputDepthTextureOverride == null
                && ShaderPacks.inUse()) {
            ShaderPacks.bindWorldTarget();
        }
    }
}
//?}
