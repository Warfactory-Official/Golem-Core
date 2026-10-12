package dev.golem.core.mixin;

//? if >=26.1 {
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
 * of vanilla's weather columns. Listed for Fabric 26.1 only; NeoForge 26.1 uses its {@code CustomWeatherEffectRenderer}.
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

    @Inject(method = "render(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void golem$weather(Vec3 camera, WeatherRenderState state, CallbackInfo ci) {
        if (PrecipitationRendering.render(camera)) ci.cancel();
    }
}
//?} else {
/*public final class MixinWeatherRender {
    // Empty below 26.1: Fabric uses DimensionRenderingRegistry there, Forge-likes MixinLegacyWeatherRender.
    private MixinWeatherRender() {}
}
*///?}
