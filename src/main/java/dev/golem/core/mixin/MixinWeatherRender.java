package dev.golem.core.mixin;

//? if >=1.21.11 {
import dev.golem.core.render.PrecipitationRendering;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1 Fabric's hook for {@link dev.golem.core.client.Precipitation}: a registered level's rain and snow quads in place
 * of vanilla's weather columns. Listed for Fabric, and for NeoForge 1.21.11, whose level renderer calls the overload that
 * passes no render state, so its {@code CustomWeatherEffectRenderer} never runs; later NeoForge uses that renderer.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla 26.1 draws the weather from its own extracted columns with the
 * texture's colour and no per-level renderer; (2) Fabric API 26.1 removed {@code DimensionRenderingRegistry} and its
 * {@code LevelRenderEvents} have no weather stage that replaces vanilla's - drawing in another stage would draw on top of
 * vanilla's rain and miss the frame graph's weather target under Fabulous, which only the weather pass declares;
 * (3) widening cannot add a call. One cancellable HEAD inject on {@code WeatherEffectRenderer.render}, inside vanilla's
 * own weather pass; it cancels only for a level registered with {@code Precipitation}.
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class MixinWeatherRender {

    // 1.21.11 passes the buffer source first; Fabric API has no DimensionRenderingRegistry there either.
    //? if >=26.3 {
    /*@Inject(method = "prepare", at = @At("HEAD"))
    private void golem$prepareWeather(Vec3 camera, WeatherRenderState state, CallbackInfo ci) {
        PrecipitationRendering.prepareFrame(camera);
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/blaze3d/systems/RenderPass;)V",
            at = @At("HEAD"), cancellable = true)
    private void golem$weather(WeatherRenderState state, com.mojang.blaze3d.systems.RenderPass pass, CallbackInfo ci) {
        if (PrecipitationRendering.drawPending(pass)) ci.cancel();
    }

    @Inject(method = "renderOit", at = @At("HEAD"), cancellable = true)
    private void golem$weatherOit(net.minecraft.client.renderer.oit.OitStage stage, WeatherRenderState state,
                                  com.mojang.blaze3d.systems.RenderPass pass, CallbackInfo ci) {
        if (PrecipitationRendering.drawPendingOit(stage, pass)) ci.cancel();
    }
    *///?} elif >=26.1 {
    @Inject(method = "render(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void golem$weather(Vec3 camera, WeatherRenderState state, CallbackInfo ci) {
        if (PrecipitationRendering.render(camera)) ci.cancel();
    }
    //?} else {
    /*@Inject(method = "render(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/Vec3;"
            + "Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void golem$weather(net.minecraft.client.renderer.MultiBufferSource buffers, Vec3 camera,
                               WeatherRenderState state, CallbackInfo ci) {
        if (PrecipitationRendering.render(camera)) ci.cancel();
    }
    *///?}
}
//?} else {
/*public final class MixinWeatherRender {
    // Empty below 26.1: Fabric uses DimensionRenderingRegistry there, Forge-likes MixinLegacyWeatherRender.
    private MixinWeatherRender() {}
}
*///?}
