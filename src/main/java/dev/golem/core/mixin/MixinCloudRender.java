package dev.golem.core.mixin;

//? if >=1.21.11 {
import dev.golem.core.render.CloudRendering;
import dev.golem.core.client.Clouds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla exposes no extra-cloud stage. Fabric has no cloud hook; NeoForge's hook precedes the native pass.
 * Widening cannot append a draw, so HEAD handles replacements and RETURN appends registered additions.
 */
@Mixin(CloudRenderer.class)
public abstract class MixinCloudRender {

    //? if >=26.3 {
    /*@Inject(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"), cancellable = true)
    private void golem$prepare(int color, CloudStatus status, float bottomY, int range, Vec3 camera, long gameTime,
                               float partialTicks, CallbackInfo ci) {
        if (CloudRendering.isLayerRenderer(this)) return;
        if (CloudRendering.prepareFrame(color, status, bottomY, range, camera, gameTime, partialTicks)) ci.cancel();
    }

    @Inject(method = "render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/blaze3d/systems/RenderPass;)V", at = @At("HEAD"),
            cancellable = true)
    private void golem$replace(CloudStatus status, com.mojang.blaze3d.systems.RenderPass pass, CallbackInfo ci) {
        if (CloudRendering.isLayerRenderer(this) || !CloudRendering.replacing()) return;
        CloudRendering.drawFrame(status, pass);
        ci.cancel();
    }

    @Inject(method = "render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/blaze3d/systems/RenderPass;)V", at = @At("RETURN"))
    private void golem$add(CloudStatus status, com.mojang.blaze3d.systems.RenderPass pass, CallbackInfo ci) {
        if (!CloudRendering.isLayerRenderer(this)) CloudRendering.drawFrame(status, pass);
    }

    @Inject(method = "renderOit", at = @At("HEAD"), cancellable = true)
    private void golem$replaceOit(CloudStatus status, net.minecraft.client.renderer.oit.OitStage stage,
                                  com.mojang.blaze3d.textures.GpuTextureView depth,
                                  net.minecraft.client.renderer.oit.OitRenderPassProvider.Parameters params, CallbackInfo ci) {
        if (CloudRendering.isLayerRenderer(this) || !CloudRendering.replacing()) return;
        CloudRendering.drawFrameOit(status, stage, depth, params);
        ci.cancel();
    }

    @Inject(method = "renderOit", at = @At("RETURN"))
    private void golem$addOit(CloudStatus status, net.minecraft.client.renderer.oit.OitStage stage,
                              com.mojang.blaze3d.textures.GpuTextureView depth,
                              net.minecraft.client.renderer.oit.OitRenderPassProvider.Parameters params, CallbackInfo ci) {
        if (!CloudRendering.isLayerRenderer(this)) CloudRendering.drawFrameOit(status, stage, depth, params);
    }

    @Inject(method = "endFrame", at = @At("HEAD"))
    private void golem$endFrame(CallbackInfo ci) {
        if (!CloudRendering.isLayerRenderer(this)) CloudRendering.endFrame();
    }
    *///?} else {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void golem$clouds(int color, CloudStatus status, float bottomY,
                              //? if >=26.1 {
                              int range,
                              //?}
                              Vec3 camera, long gameTime, float partialTicks, CallbackInfo ci) {
        //? if <26.1 {
        /*int range = Minecraft.getInstance().options.cloudRange().get() * 16;
        *///?}
        if (CloudRendering.isLayerRenderer(this)) return;
        if (!Clouds.nativeVisible(Minecraft.getInstance().level)
                || CloudRendering.render(status, range, camera)) ci.cancel();
    }
    @Inject(method = "render", at = @At("RETURN"))
    private void golem$additions(int color, CloudStatus status, float bottomY,
                                 //? if >=26.1 {
                                 int range,
                                 //?}
                                 Vec3 camera, long gameTime, float partialTicks, CallbackInfo ci) {
        //? if <26.1 {
        /*int range = Minecraft.getInstance().options.cloudRange().get() * 16;
        *///?}
        if (CloudRendering.isLayerRenderer(this) || !Clouds.nativeVisible(Minecraft.getInstance().level)) return;
        CloudRendering.renderAdditions(color, status, bottomY, range, camera, gameTime, partialTicks);
    }
    //?}

}
//?} else {
/*public final class MixinCloudRender {
    // Empty below 26.1: Fabric uses DimensionRenderingRegistry there, Forge-likes MixinLegacyCloudRender.
    private MixinCloudRender() {}
}
*///?}
