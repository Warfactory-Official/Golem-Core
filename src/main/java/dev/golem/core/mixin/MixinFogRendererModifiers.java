package dev.golem.core.mixin;

import dev.golem.core.client.Fogs;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Fabric's half of {@link Fogs}: the final fog colour and the linear fog distance, where Forge-like loaders fire
 * {@code ViewportEvent.ComputeFogColor} and {@code ViewportEvent.RenderFog}.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla computes both inside {@code FogRenderer} with no extension point
 * (26.1's environment attributes feed the colour <i>before</i> the sky, weather and fluid blend, and the legacy
 * behaviour changes the final colour); (2) Fabric API has no fog event (Forge and NeoForge use their events, so this
 * class is listed only in {@code golemcore.fabric.mixins.json}); (3) widening cannot help, the values must change inside
 * the method. One RETURN/TAIL inject per method; nothing changes unless a {@link Fogs.Modifier} is active.
 */
//? if >=26.1 {
@Mixin(net.minecraft.client.renderer.fog.FogRenderer.class)
public abstract class MixinFogRendererModifiers {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void golem$fogs(net.minecraft.client.Camera camera, int renderDistanceInChunks,
                            net.minecraft.client.DeltaTracker deltaTracker, float darkenWorldAmount,
                            net.minecraft.client.multiplayer.ClientLevel level,
                            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.client.renderer.fog.FogData> cir) {
        Entity entity = Minecraft.getInstance().getCameraEntity();
        if (!Fogs.active(entity)) return;
        net.minecraft.client.renderer.fog.FogData data = cir.getReturnValue();
        Vec3 color = Fogs.color(entity, new Vec3(data.color.x(), data.color.y(), data.color.z()));
        data.color.set((float) color.x, (float) color.y, (float) color.z, data.color.w());
        Fogs.applyDistances(data, entity);
    }
}
//?} else {
/*@Mixin(net.minecraft.client.renderer.FogRenderer.class)
public abstract class MixinFogRendererModifiers {

    @org.spongepowered.asm.mixin.Shadow private static float fogRed;
    @org.spongepowered.asm.mixin.Shadow private static float fogGreen;
    @org.spongepowered.asm.mixin.Shadow private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void golem$fogColor(net.minecraft.client.Camera camera, float partialTick,
                                       net.minecraft.client.multiplayer.ClientLevel level, int renderDistance,
                                       float darkenWorldAmount,
                                       org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Entity entity = Minecraft.getInstance().getCameraEntity();
        if (!Fogs.active(entity)) return;
        Vec3 color = Fogs.color(entity, new Vec3(fogRed, fogGreen, fogBlue));
        fogRed = (float) color.x;
        fogGreen = (float) color.y;
        fogBlue = (float) color.z;
        com.mojang.blaze3d.systems.RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void golem$fogDistance(net.minecraft.client.Camera camera,
                                          net.minecraft.client.renderer.FogRenderer.FogMode mode, float farPlaneDistance,
                                          boolean thickFog, float partialTick,
                                          org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Entity entity = Minecraft.getInstance().getCameraEntity();
        float far = Fogs.far(entity, Fogs.renderDistanceBlocks());
        if (Float.isNaN(far)) return;
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(0.0F);
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(far);
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
    }
}
*///?}
