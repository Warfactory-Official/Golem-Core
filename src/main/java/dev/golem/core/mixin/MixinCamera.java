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
 * Moves third-person flight view down by 1.7's 1.22 blocks.
 *
 * <p>Rungs 1 and 2 have no cross-loader camera-position event, and an access widener cannot expose a
 * callback. The smallest remaining hook is an invoker for vanilla's protected {@code Camera.move} plus a
 * TAIL injection into the public setup method; it does not touch private state or transform bytecode.
 *
 * <p>{@link CameraHooks#DISTANCE} (the detached camera's distance, 1.7 {@code thirdPersonDistance}) is one
 * {@code @ModifyArg} on the distance vanilla hands {@code getMaxZoom}, so the block clipping still applies. Rung 2:
 * NeoForge has {@code CalculateDetachedCameraDistanceEvent}, but Forge 1.20.1 and fabric-api have no camera-distance
 * event, and 26.1's {@code camera_distance} attribute is server-synced entity state, not a client view setting; one
 * argument modifier here serves all six nodes the same way.
 *
 * <p>{@link CameraHooks#PITCH} turns the placed camera by re-setting its rotation at the end of the method that placed
 * it, through vanilla's protected {@code setRotation}. Rung 2: NeoForge and Forge have {@code ComputeCameraAngles},
 * fabric-api has no camera-angle event; the TAIL inject serves all six nodes the same way and leaves the position alone.
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
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float golem$thirdPersonDistance(float distance) {
        return CameraHooks.thirdPersonDistance(((net.minecraft.client.Camera) (Object) this).entity(), distance);
    }
    //?} elif >=1.21.1 {
    /*@org.spongepowered.asm.mixin.injection.ModifyArg(method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float golem$thirdPersonDistance(float distance) {
        return CameraHooks.thirdPersonDistance(((net.minecraft.client.Camera) (Object) this).getEntity(), distance);
    }
    *///?} else {
    /*@org.spongepowered.asm.mixin.injection.ModifyArg(method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"))
    private double golem$thirdPersonDistance(double distance) {
        return CameraHooks.thirdPersonDistance(((net.minecraft.client.Camera) (Object) this).getEntity(), (float) distance);
    }
    *///?}

    @org.spongepowered.asm.mixin.Shadow
    protected abstract void setRotation(float yRot, float xRot);

    //? if >=26.1 {
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void golem$pitchOffset(float partialTick, CallbackInfo ci) {
        net.minecraft.client.Camera camera = (net.minecraft.client.Camera) (Object) this;
        float offset = CameraHooks.pitchOffset(camera.entity());
        if (offset != 0F) setRotation(camera.yRot(), camera.xRot() + offset);
    }
    //?} else {
    /*@Inject(method = "setup", at = @At("TAIL"))
    private void golem$pitchOffset(net.minecraft.world.level.BlockGetter level, Entity entity, boolean detached,
            boolean mirror, float partialTick, CallbackInfo ci) {
        net.minecraft.client.Camera camera = (net.minecraft.client.Camera) (Object) this;
        float offset = CameraHooks.pitchOffset(entity);
        if (offset != 0F) setRotation(camera.getYRot(), camera.getXRot() + offset);
    }
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
