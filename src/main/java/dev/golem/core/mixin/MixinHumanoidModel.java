package dev.golem.core.mixin;

import dev.golem.core.client.PlayerRenderHooks;
import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla exposes no post-animation callback. Loader render events run before setupAnim (overwritten)
// or after the body draw (too late), and access widening cannot add a timing hook. A TAIL injection
// also runs before legacy PlayerModel copies the skin overlays, keeping every layer on the same pose.
@Mixin(HumanoidModel.class)
public class MixinHumanoidModel {
    //? if >=26.1 {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void golem$pose(net.minecraft.client.renderer.entity.state.HumanoidRenderState state, CallbackInfo ci) {
        PlayerRenderHooks.applyModel(PlayerRenderHooks.playerForState(state), (HumanoidModel<?>) (Object) this);
    }
    //?} else {
    /*@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void golem$pose(net.minecraft.world.entity.LivingEntity entity, float limbSwing, float limbAmount,
            float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof net.minecraft.world.entity.player.Player player)
            PlayerRenderHooks.applyModel(player, (HumanoidModel<?>) (Object) this);
    }
    *///?}
}
