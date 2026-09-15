package dev.golem.core.render;

import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionf;

/**
 * The camera information a block-entity renderer needs while submitting geometry.
 *
 * <p>26.1 passes a {@code CameraRenderState} into {@code submit}; older versions expose the camera
 * through {@code Minecraft.getInstance().gameRenderer.getMainCamera()} instead. This carries only
 * the three members renderers actually use, so the two sources can be normalised into one type.
 */
public final class CameraInfo {

    /** Camera position in world space. */
    public final Vec3 pos;
    /** Camera yaw in degrees, used to billboard geometry toward the viewer. */
    public final float yRot;
    /** Camera orientation, used to billboard geometry toward the viewer. */
    public final Quaternionf orientation;

    public CameraInfo(Vec3 pos, float yRot, Quaternionf orientation) {
        this.pos = pos;
        this.yRot = yRot;
        this.orientation = orientation;
    }

    /** The active world camera, normalized across the supported renderer APIs. */
    public static CameraInfo current() {
        //? if >=26.1 {
        var state = Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        return new CameraInfo(state.pos, state.yRot, state.orientation);
        //?} else {
        /*net.minecraft.client.Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        return new CameraInfo(camera.getPosition(), camera.getYRot(), camera.rotation());
        *///?}
    }

    /** Converts a camera-relative world vector to the coordinate space of a world renderer's pose. */
    public org.joml.Vector3f worldToRender(org.joml.Vector3f vector) {
        // 1.20 bakes the view rotation into the world PoseStack; newer renderers keep it in the
        // global shader matrix. InverseViewRotation also includes the loader's camera roll.
        //? if <1.21 {
        /*vector.mul(new org.joml.Matrix3f(com.mojang.blaze3d.systems.RenderSystem.getInverseViewRotationMatrix()).invert());
        *///?}
        return vector;
    }

    /** Inverse of worldToRender, for local equipment attachments retained as world-space history. */
    public org.joml.Vector3f renderToWorld(org.joml.Vector3f vector) {
        //? if <1.21 {
        /*vector.mul(com.mojang.blaze3d.systems.RenderSystem.getInverseViewRotationMatrix());
        *///?}
        return vector;
    }
}
