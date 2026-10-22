package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightFrame;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void gemrender$beginLights(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                                       GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix,
                                       Matrix4f projectionMatrix, CallbackInfo ci) {
        LightFrame.getInstance()
                .begin(camera, frustumMatrix, projectionMatrix, deltaTracker.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void gemrender$occupancyLoad(ChunkPos pos, CallbackInfo ci) {
        LightFrame.chunkChanged(pos.x, pos.z);
    }
}
