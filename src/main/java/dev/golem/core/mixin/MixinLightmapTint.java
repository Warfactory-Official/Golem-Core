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
        if (!renderState.needsUpdate) return;
        Lightmaps.apply(((Lightmap) (Object) this).getTextureView(), new Lightmaps.Frame(renderState.skyFactor,
                renderState.blockFactor, renderState.brightness, renderState.darknessEffectScale,
                renderState.nightVisionEffectIntensity, renderState.bossOverlayWorldDarkening));
    }
}
//?} elif >=1.21.11 {
/*import dev.golem.core.client.Lightmaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * {@link Lightmaps} on 1.21.11, whose light map is one GPU pass like 26.1's but has no render state: the factors are
 * read again from the sources {@code updateLightTexture} reads, after its pass, on the frames it drew.
 ^/
@Mixin(LightTexture.class)
public abstract class MixinLightmapTint {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GameRenderer renderer;
    @Shadow @Final private com.mojang.blaze3d.textures.GpuTextureView textureView;
    @Shadow private boolean updateLightTexture;
    @Shadow private float blockLightRedFlicker;
    @Unique private boolean golem$updating;

    @Inject(method = "updateLightTexture", at = @At("HEAD"))
    private void golem$updating(float partialTick, CallbackInfo ci) {
        golem$updating = updateLightTexture;
    }

    @Inject(method = "updateLightTexture", at = @At("TAIL"))
    private void golem$additions(float partialTick, CallbackInfo ci) {
        var level = minecraft.level;
        var player = minecraft.player;
        if (!golem$updating || level == null || player == null) return;
        var probe = renderer.getMainCamera().attributeProbe();
        float sky = probe.getValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_LIGHT_FACTOR, partialTick);
        var endFlash = level.endFlashState();
        if (endFlash != null && !minecraft.options.hideLightningFlash().get()) {
            float intensity = endFlash.getIntensity(partialTick);
            sky += minecraft.gui.getBossOverlay().shouldCreateWorldFog() ? intensity / 3.0F : intensity;
        }
        float darknessOption = minecraft.options.darknessEffectScale().get().floatValue();
        float darkness = player.getEffectBlendFactor(MobEffects.DARKNESS, partialTick) * darknessOption;
        float waterVision = player.getWaterVision();
        float nightVision = player.hasEffect(MobEffects.NIGHT_VISION) ? GameRenderer.getNightVisionScale(player, partialTick)
                : waterVision > 0.0F && player.hasEffect(MobEffects.CONDUIT_POWER) ? waterVision : 0.0F;
        Lightmaps.apply(textureView, new Lightmaps.Frame(sky, blockLightRedFlicker + 1.4F,
                Math.max(0.0F, minecraft.options.gamma().get().floatValue() - darkness),
                golem$darknessScale(player, darkness, partialTick) * darknessOption, nightVision,
                renderer.getDarkenWorldAmount(partialTick)));
    }

    /^* LightTexture.calculateDarknessScale, which is private. ^/
    @Unique
    private static float golem$darknessScale(LivingEntity entity, float darkness, float partialTick) {
        return Math.max(0.0F, net.minecraft.util.Mth.cos((entity.tickCount - partialTick) * (float) Math.PI * 0.025F)
                * 0.45F * darkness);
    }
}
*///?} else {
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
