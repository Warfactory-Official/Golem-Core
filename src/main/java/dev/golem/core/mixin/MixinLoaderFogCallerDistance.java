package dev.golem.core.mixin;

//? if <26.1 {
/*import com.mojang.blaze3d.shaders.FogShape;
*///?}
//? if >=26.1 {
import net.minecraft.client.renderer.fog.FogData;
//?}

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.golem.core.client.Fogs;
import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Vanilla has no loader-event hook, and the loader event omits its caller distance.
 * Widening cannot attach metadata, so bind it before the unchanged event is posted.
 */
@Pseudo
@Mixin(targets = {"net.neoforged.neoforge.client.ClientHooks",
        "net.minecraftforge.client.ForgeHooksClient"}, remap = false)
public abstract class MixinLoaderFogCallerDistance {
    //? if >=26.1 {
    @Coerce
    @WrapOperation(method = "onSetupFog", at = @At(value = "NEW",
            target = "net/neoforged/neoforge/client/event/ViewportEvent$RenderFog"), require = 1, allow = 1)
    private static Object golem$callerDistance(@Coerce Object environment, FogType type, Camera camera,
                                              float partialTick, FogData fogData,
                                              Operation<Object> original,
                                              @Local(argsOnly = true, ordinal = 1) float renderDistanceChunks) {
        return Fogs.bindCallerDistance(original.call(environment, type, camera, partialTick, fogData),
                renderDistanceChunks * 16.0F);
    }
    //?} else {
    /*@Coerce
    @WrapOperation(method = "onFogRender", at = {
            @At(value = "NEW", target = "net/neoforged/neoforge/client/event/ViewportEvent$RenderFog"),
            @At(value = "NEW", target = "net/minecraftforge/client/event/ViewportEvent$RenderFog")}, require = 1, allow = 1)
    private static Object golem$callerDistance(@Coerce Object mode, FogType type, Camera camera,
                                              float partialTick, float nearDistance, float farDistance,
                                              FogShape shape, Operation<Object> original,
                                              @Local(argsOnly = true, ordinal = 1) float renderDistanceBlocks) {
        return Fogs.bindCallerDistance(original.call(mode, type, camera, partialTick, nearDistance, farDistance, shape),
                renderDistanceBlocks);
    }
    *///?}
}
