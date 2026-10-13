package dev.golem.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;

/**
 * Puts a pose into camera space, for something drawn locked to the view: a first-person weapon body.
 *
 * <p>Both eras start with {@link PoseStack#setIdentity()}, discarding the arm/equip transforms vanilla
 * has stacked up by the time an item renderer is reached. What identity means differs:
 *
 * <ul>
 *   <li><b>1.21 and up</b>: the view transform lives outside the pose stack (in the render system's
 *       modelview), so an identity pose is world space and the camera's rotation has to be applied.
 *   <li><b>1.20.1</b>: the view rotation is inside the pose stack vanilla hands down, so an identity
 *       pose is already camera space. Applying the camera rotation there rotates the weapon twice and
 *       it counter-rotates as the player looks around.
 * </ul>
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
