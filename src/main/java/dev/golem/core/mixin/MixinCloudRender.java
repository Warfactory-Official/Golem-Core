package dev.golem.core.mixin;

//? if >=26.1 {

import dev.golem.core.render.CloudRendering;
import dev.golem.core.client.Clouds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla exposes no extra-cloud stage. Fabric has no cloud hook; NeoForge's hook precedes the native pass.
 * Widening cannot append a draw, so HEAD handles replacements and RETURN appends registered additions.
 */
@Mixin(CloudRenderer.class)
public abstract class MixinCloudRender {

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"), index = 0)
    private com.mojang.blaze3d.pipeline.RenderPipeline golem$layerPipeline(
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline) {
        return CloudRendering.isLayerRenderer(this) ? CloudRendering.layerPipeline(pipeline) : pipeline;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void golem$clouds(int color, CloudStatus status, float bottomY, int range, Vec3 camera, long gameTime,
                              float partialTicks, CallbackInfo ci) {
        if (CloudRendering.isLayerRenderer(this)) return;
        if (!Clouds.nativeVisible(Minecraft.getInstance().level)
                || CloudRendering.render(status, range, camera)) ci.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void golem$additions(int color, CloudStatus status, float bottomY, int range, Vec3 camera, long gameTime,
                                 float partialTicks, CallbackInfo ci) {
        if (CloudRendering.isLayerRenderer(this) || !Clouds.nativeVisible(Minecraft.getInstance().level)) return;
        CloudRendering.renderAdditions(color, status, bottomY, range, camera, gameTime, partialTicks);
    }

}
//?} else {
/*public final class MixinCloudRender {
    // Empty below 26.1: Fabric uses DimensionRenderingRegistry there, Forge-likes MixinLegacyCloudRender.
    private MixinCloudRender() {}
}
*///?}
