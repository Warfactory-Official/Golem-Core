package dev.golem.core.mixin;

import dev.golem.core.client.PlayerRenderHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// This is a last-rung hook, not a version workaround. Vanilla and loader APIs provide no callback between
// the living renderer's root pose and HumanoidModel submission; applying a flight tilt from a later layer
// would rotate only the jetpack. An access widener changes visibility but cannot create that timing point.
// The injection is one HEAD callback and the Core hook keeps feature semantics out of this mixin.
//? if >=26.1 {
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

@Mixin(AvatarRenderer.class)
public class MixinLivingEntityRenderer {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void golem$bindState(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (entity instanceof net.minecraft.world.entity.player.Player player)
            PlayerRenderHooks.bindState(state, player);
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
        if (entity instanceof net.minecraft.world.entity.player.Player player)
            PlayerRenderHooks.apply(player, pose, partialTicks);
    }
}
*///?}
