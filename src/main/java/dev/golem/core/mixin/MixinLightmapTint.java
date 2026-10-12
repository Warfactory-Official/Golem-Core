package dev.golem.core.mixin;

//? if >=26.1 {
import dev.golem.core.client.Lightmaps;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link Lightmaps}: a mod's additions to the light map, the stand-in for 1.7's provider light-map hooks.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla offers no light-map hook - on 26.1 the map is one GPU pass from
 * environment attributes whose fixed formula ({@code color += SkyLightColor * sky_brightness}) cannot express a
 * per-cell addition such as 1.7's {@code max(sky - sun, 0)}; (2) no loader event fires after the light map is drawn
 * (NeoForge's {@code adjustLightmapColors} is per dimension-effects object, absent on 26.1 and on Fabric); (3) widening
 * cannot run code at that moment. One TAIL {@code @Inject} on {@code Lightmap.render} that draws a second, additive pass
 * only when vanilla updated the map this frame and a modifier is active. The same pass draws
 * {@link Lightmaps#useClassicFormula}'s pre-26.1 cells over vanilla's for a registered level (no second hook).
 */
@Mixin(Lightmap.class)
public abstract class MixinLightmapTint {

    @Inject(method = "render", at = @At("TAIL"))
    private void golem$additions(LightmapRenderState renderState, CallbackInfo ci) {
        if (renderState.needsUpdate) Lightmaps.apply(((Lightmap) (Object) this).getTextureView(), renderState);
    }
}
//?} else {
/*import com.mojang.blaze3d.platform.NativeImage;
import dev.golem.core.client.Lightmaps;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * {@link Lightmaps}: a mod's additions to the light map, the stand-in for 1.7's provider light-map hooks.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla offers no light-map hook; (2) NeoForge's and Forge's
 * {@code adjustLightmapColors} belongs to a dimension's special-effects object (one per effects id, replaced by
 * whoever registers it) and Fabric has none; (3) widening the pixel field would still need code at the moment the map
 * is uploaded. One {@code @Inject} before the light map's {@code DynamicTexture.upload()} in
 * {@code updateLightTexture}, which edits the pixels only while a modifier is active.
 ^/
@Mixin(LightTexture.class)
public abstract class MixinLightmapTint {

    @Shadow @Final private NativeImage lightPixels;

    @Inject(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
    private void golem$additions(float partialTick, CallbackInfo ci) {
        Lightmaps.apply(this.lightPixels);
    }
}
*///?}
