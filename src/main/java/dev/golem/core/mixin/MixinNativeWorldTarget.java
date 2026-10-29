package dev.golem.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.golem.core.client.ShaderPacks;
import dev.golem.core.render.WorldEffects;
import dev.golem.core.render.CloudRendering;

import java.util.Collection;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=26.1 {
import com.mojang.blaze3d.opengl.GlRenderPass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
//?}

// Vanilla and loader world-render events precede the encoder's target binding.
// Widening cannot insert a callback after pipeline setup for the native draw.
//? if >=26.1 {
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
//?} else {
/*@Mixin(GameRenderer.class)
 *///?}
public abstract class MixinNativeWorldTarget {
    //? if >=26.1 {
    @Inject(method = "trySetup", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/opengl/GlCommandEncoder;applyPipelineState(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V",
            shift = At.Shift.AFTER))
    private void golem$nativeWorldTarget(GlRenderPass pass, Collection<String> uniforms,
                                         CallbackInfoReturnable<Boolean> callback, @Local RenderPipeline pipeline) {
        // Core's native shader factories reserve this prefix. GUI, private-target and shadow draws keep their targets.
        boolean nativeEffect = pipeline.getLocation().getPath()
                                       .startsWith("pipeline/golem") && WorldEffects.isCollecting()
                && RenderSystem.outputColorTextureOverride == null && RenderSystem.outputDepthTextureOverride == null
                || CloudRendering.isDrawingLayers();
        if (nativeEffect && ShaderPacks.inUse()) {
            ShaderPacks.bindWorldTarget();
        }
    }
    //?}
}
