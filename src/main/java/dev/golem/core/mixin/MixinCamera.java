package dev.golem.core.mixin;

import dev.golem.core.client.CameraHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moves third-person flight view down by the old 1.22 blocks.
 *
 * <p>Rungs 1 and 2 have no cross-loader camera-position event, and an access widener cannot expose a
 * callback. The smallest remaining hook is an invoker for vanilla's protected {@code Camera.move} plus a
 * TAIL injection into the public setup method; it does not touch private state or transform bytecode.
 */
@Mixin(net.minecraft.client.Camera.class)
public abstract class MixinCamera {

    //? if >=1.21.1 && <26.1 {
    /*@Invoker("move")
    protected abstract void golem$move(float x, float y, float z);
    *///?} else if <1.21.1 {
    /*@Invoker("move")
    protected abstract void golem$move(double x, double y, double z);
    *///?}

    //? if >=26.1 {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void golem$adjustRenderState(net.minecraft.client.renderer.state.level.CameraRenderState state,
            float partialTick, CallbackInfo ci) {
        net.minecraft.client.Camera camera = (net.minecraft.client.Camera) (Object) this;
        Entity entity = camera.entity();
        if (camera.isDetached() && entity instanceof Player player && CameraHooks.lowerThirdPersonCamera(player)) {
            state.pos = state.pos.add(0D, -1.22D, 0D);
        }
    }
    //?} else {
    /*
    @Inject(method = "setup", at = @At("TAIL"))
    private void golem$adjustThirdPerson(net.minecraft.world.level.BlockGetter level, Entity entity,
            boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
        if (detached && entity instanceof Player player && CameraHooks.lowerThirdPersonCamera(player)) {
            //? if >=1.21.1 {
            golem$move(0F, -1.22F, 0F);
            //?} else {
            /^golem$move(0D, -1.22D, 0D);^/
            //?}
        }
    }
    *///?}
}
