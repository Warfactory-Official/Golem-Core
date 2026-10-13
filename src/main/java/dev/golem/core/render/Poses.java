package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Operations on a {@link PoseStack.Pose} that are spelled differently across versions.
 *
 * <p>26.1 added mutating helpers directly on {@code Pose}, so geometry callbacks can adjust the pose
 * they were handed: billboarding toward the camera, for instance. Pre-26.1 {@code Pose} is a passive
 * pair of matrices with no such helpers; the equivalent lives on {@code PoseStack} itself, which the
 * callback does not have.
 *
 * <p>These are static helpers, not a wrapper type, because {@code Pose} is passed straight to
 * {@code VertexConsumer.addVertex(pose, ...)} on both eras. Call {@code Poses.rotate(pose, q)} instead of
 * {@code pose.rotate(q)}.
 */
public final class Poses {

    private Poses() {}

    /** Applies an affine model matrix, including its mirrored/nonuniform normal transform. */
    public static void mulPose(PoseStack poseStack, Matrix4f transform) {
        //? if >=1.20.5 {
        poseStack.mulPose(transform);
        //?} else {
        /*poseStack.mulPoseMatrix(transform);
        poseStack.last().normal().mul(new org.joml.Matrix3f(transform).invert().transpose());
        *///?}
    }

    /**
     * Rotates the pose in place, applying the rotation to both the position and normal matrices,
     * as {@code PoseStack.mulPose} does to the top of the stack.
     */
    public static void rotate(PoseStack.Pose pose, Quaternionf rotation) {
        //? if >=26.1 {
        pose.rotate(rotation);
        //?} else {
        /*pose.pose().rotate(rotation);
        pose.normal().rotate(rotation);
        *///?}
    }

    /**
     * Scales a pose in place. 26.1 exposes this on {@code Pose}; older versions only expose the
     * matrices, so it goes through the transform matrix directly. Uniform scales leave normal
     * directions unchanged, which is why the normal matrix is untouched.
     */
    public static void scale(PoseStack.Pose pose, float x, float y, float z) {
        //? if >=26.1 {
        pose.scale(x, y, z);
        //?} else {
        /*pose.pose().scale(x, y, z);
        *///?}
    }

    /** Translates a pose in place. Same split as {@link #scale}; a translation never affects normals. */
    public static void translate(PoseStack.Pose pose, float x, float y, float z) {
        //? if >=26.1 {
        pose.translate(x, y, z);
        //?} else {
        /*pose.pose().translate(x, y, z);
        *///?}
    }
}
