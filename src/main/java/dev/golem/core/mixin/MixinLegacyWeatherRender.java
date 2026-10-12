package dev.golem.core.mixin;

//? if <26.1 {
/*import dev.golem.core.render.PrecipitationRendering;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * The 1.21.1 NeoForge / 1.20.1 Forge hook for {@link dev.golem.core.client.Precipitation}: a registered level's rain and
 * snow quads in place of vanilla's. Fabric uses Fabric API's {@code DimensionRenderingRegistry.registerWeatherRenderer}
 * at the same place instead.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla draws the weather in {@code LevelRenderer.renderSnowAndRain} with
 * the texture's colour; (2) the Forge-likes' only seam is the dimension type's effects object
 * ({@code renderSnowAndRain}), chosen by the type's {@code effects} id, which a mod's body dimensions share with the
 * overworld (the same reason as {@code MixinLegacyCloudRender}); (3) widening cannot add a call. One cancellable HEAD
 * inject on {@code renderSnowAndRain}, before the effects check; it cancels only for a level registered with
 * {@code Precipitation}. Rain particles and sounds ({@code tickRain}) are untouched.
 ^/
@Mixin(LevelRenderer.class)
public abstract class MixinLegacyWeatherRender {

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void golem$weather(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ,
                               CallbackInfo ci) {
        if (PrecipitationRendering.renderLegacy(lightTexture, partialTick, camX, camY, camZ)) ci.cancel();
    }
}
*///?} else {
public final class MixinLegacyWeatherRender {
    // Empty on 26.1: NeoForge uses CustomWeatherEffectRenderer, Fabric MixinWeatherRender.
    private MixinLegacyWeatherRender() {}
}
//?}
