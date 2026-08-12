package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;

/**
 * Operations on a {@link PoseStack.Pose} that are spelled differently across versions.
 *
 * <p>26.1 added mutating helpers directly on {@code Pose}, so geometry callbacks can adjust the pose
 * they were handed — billboarding toward the camera, for instance. Pre-26.1 {@code Pose} is a passive
 * pair of matrices with no such helpers; the equivalent lives on {@code PoseStack} itself, which the
 * callback does not have.
 *
 * <p>These are static helpers rather than a wrapper type on purpose: {@code Pose} is passed straight
 * to {@code VertexConsumer.addVertex(pose, ...)} on both eras, so wrapping it would force every call
 * site to unwrap again. Call {@code Poses.rotate(pose, q)} instead of {@code pose.rotate(q)} and
 * nothing else about a renderer changes.
 */
public final class Poses {

    private Poses() {}

    /**
     * Rotates the pose in place, applying the rotation to both the position and normal matrices —
     * matching what {@code PoseStack.mulPose} does to the top of the stack.
     */
    public static void rotate(PoseStack.Pose pose, Quaternionf rotation) {
        //? if >=26.1 {
        pose.rotate(rotation);
        //?} else {
        /*pose.pose().rotate(rotation);
        pose.normal().rotate(rotation);
        *///?}
    }
}
