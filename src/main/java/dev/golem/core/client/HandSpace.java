package dev.golem.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;

/**
 * Puts a pose into camera space, for something drawn locked to the view — a first-person weapon body.
 *
 * <p>Both eras start the same way: {@link PoseStack#setIdentity()}, discarding the arm/equip transforms
 * vanilla has stacked up by the time an item renderer is reached. What identity <em>means</em> is where
 * they differ.
 *
 * <ul>
 *   <li><b>1.21 and up</b> — the view transform lives outside the pose stack (in the render system's
 *       modelview), so an identity pose is <em>world</em> space and the camera's rotation has to be
 *       applied to get back to the view.
 *   <li><b>1.20.1</b> — the view rotation is inside the pose stack vanilla hands down, so an identity
 *       pose is <em>already</em> camera space. Applying the camera rotation there rotates the weapon a
 *       second time, and it visibly counter-rotates as the player looks around.
 * </ul>
 *
 * <p>The 1.20.1 branch was established from behaviour rather than source: 26.1 and 1.21.1 both need the
 * rotation and 1.20.1 was doubly-rotated without this split. If a first-person body ever drifts again,
 * this is the first thing to suspect.
 *
 * <p>Client-only.
 */
public final class HandSpace {

    private HandSpace() {}

    /** Resets {@code pose} to camera space. */
    public static void enterCameraSpace(PoseStack pose) {
        pose.setIdentity();
        //? if >=1.21 {
        pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        //?}
    }
}
