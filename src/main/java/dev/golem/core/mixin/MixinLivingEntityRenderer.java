package dev.golem.core.mixin;

import dev.golem.core.client.PlayerRenderHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla exposes no extraction or post-rotation callback. Fabric lacks a mutable state event;
// loader Pre events precede root rotations. Widening visibility cannot add either timing point.
//? if >=26.1 {
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

@Mixin(AvatarRenderer.class)
public class MixinLivingEntityRenderer {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void golem$bindState(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (entity instanceof Player player) {
            PlayerRenderHooks.bindState(state, player);
            //? if fabric {
            /*PlayerRenderHooks.applyFlightState(state);
             *///?}
        }
    }

    @Inject(method = "setupRotations", at = @At("HEAD"))
    private void golem$applyPlayerPose(AvatarRenderState state, PoseStack pose, float bodyRot, float scale,
            CallbackInfo ci) {
        PlayerRenderHooks.applyState(state, pose, dev.golem.core.client.FrameTime.partialTick());
    }
}
//?} else {
/*import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    @Inject(method = "setupRotations", at = @At("HEAD"))
    //? if >=1.21.1 {
    private void golem$applyPlayerPose(LivingEntity entity, PoseStack pose, float ageInTicks, float rotationYaw,
            float partialTicks, float headYaw, CallbackInfo ci) {
    //?} else {
    /^private void golem$applyPlayerPose(LivingEntity entity, PoseStack pose, float ageInTicks, float rotationYaw,
            float partialTicks, CallbackInfo ci) {^/
    //?}
        if (entity instanceof Player player)
            PlayerRenderHooks.apply(player, pose, partialTicks);
    }

    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void golem$applyFlightPose(CallbackInfo callback, @Local(argsOnly = true) LivingEntity entity,
                                      @Local(argsOnly = true) PoseStack pose) {
        if (entity instanceof Player player) PlayerRenderHooks.applyFlightRotation(player, pose);
    }
}
*///?}
