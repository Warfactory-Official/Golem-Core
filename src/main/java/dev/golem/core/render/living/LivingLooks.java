package dev.golem.core.render.living;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Vanilla's hurt flash and death roll, for a mob a plain {@code CompatEntityRenderer} draws with its own geometry. */
public final class LivingLooks {

    private LivingLooks() {}

    public static int overlay(LivingEntity entity) {
        return OverlayTexture.pack(OverlayTexture.u(0F), OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0));
    }

    /** Vanilla's corpse roll in degrees, 0 while alive, reaching {@code maxDegrees} on the 14th tick of dying. */
    public static float deathTilt(LivingEntity entity, float partialTicks, float maxDegrees) {
        if (entity.deathTime <= 0) return 0F;
        float f = Mth.sqrt((entity.deathTime + partialTicks - 1F) / 20F * 1.6F);
        return Math.min(f, 1F) * maxDegrees;
    }

    /** Applies the roll where vanilla does: right after the {@code 180 - bodyYaw} turn, before the model's flip. */
    public static void tilt(PoseStack pose, float degrees) {
        if (degrees != 0F) pose.mulPose(Axis.ZP.rotationDegrees(degrees));
    }
}
