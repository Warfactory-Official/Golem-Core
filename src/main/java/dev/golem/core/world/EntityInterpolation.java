package dev.golem.core.world;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side position interpolation for a custom non-living entity, under 26.1's
 * {@code InterpolationHandler} surface on every node: the entity owns one, hands it back from
 * {@code interpolation()} on its Golem facade, and calls {@link #interpolate()} at the top of its client tick.
 *
 * <p>26.1+ this wraps the vanilla handler the facade returns from {@code getInterpolation()}, so the
 * server's position packets reach it through vanilla. Below 26.1 the facade routes {@code lerpTo} into
 * {@link #interpolateTo} and this class lerps the way {@code lerpPositionAndRotationStep} did.
 */
public final class EntityInterpolation {

    public static final int DEFAULT_STEPS = 3;

    //? if >=26.1 {
    private final net.minecraft.world.entity.InterpolationHandler handler;

    public EntityInterpolation(Entity entity) {
        this(entity, DEFAULT_STEPS);
    }

    public EntityInterpolation(Entity entity, int steps) {
        this.handler = new net.minecraft.world.entity.InterpolationHandler(entity, steps);
    }

    /** The vanilla handler the entity's {@code getInterpolation()} must return. */
    public net.minecraft.world.entity.InterpolationHandler handler() {
        return handler;
    }

    public Vec3 position() { return handler.position(); }
    public float yRot() { return handler.yRot(); }
    public float xRot() { return handler.xRot(); }
    public void interpolateTo(Vec3 position, float yRot, float xRot) { handler.interpolateTo(position, yRot, xRot); }
    public boolean hasActiveInterpolation() { return handler.hasActiveInterpolation(); }
    public void setInterpolationLength(int steps) { handler.setInterpolationLength(steps); }
    public void interpolate() { handler.interpolate(); }
    public void cancel() { handler.cancel(); }
    //?} else {
    /*private final Entity entity;
    private int interpolationSteps;
    private int steps;
    private Vec3 target = Vec3.ZERO;
    private float targetYRot;
    private float targetXRot;

    public EntityInterpolation(Entity entity) {
        this(entity, DEFAULT_STEPS);
    }

    public EntityInterpolation(Entity entity, int steps) {
        this.entity = entity;
        this.interpolationSteps = steps;
    }

    public Vec3 position() { return steps > 0 ? target : entity.position(); }
    public float yRot() { return steps > 0 ? targetYRot : entity.getYRot(); }
    public float xRot() { return steps > 0 ? targetXRot : entity.getXRot(); }

    public void interpolateTo(Vec3 position, float yRot, float xRot) {
        if (interpolationSteps == 0) {
            entity.moveTo(position.x, position.y, position.z, yRot, xRot);
            cancel();
            return;
        }
        steps = interpolationSteps;
        target = position;
        targetYRot = yRot;
        targetXRot = xRot;
    }

    public boolean hasActiveInterpolation() { return steps > 0; }
    public void setInterpolationLength(int steps) { this.interpolationSteps = steps; }

    public void interpolate() {
        if (steps <= 0) return;
        double alpha = 1.0D / steps;
        double x = Mth.lerp(alpha, entity.getX(), target.x);
        double y = Mth.lerp(alpha, entity.getY(), target.y);
        double z = Mth.lerp(alpha, entity.getZ(), target.z);
        float newYRot = (float) (entity.getYRot() + Mth.wrapDegrees(targetYRot - entity.getYRot()) * alpha);
        float newXRot = (float) (entity.getXRot() + (targetXRot - entity.getXRot()) * alpha);
        entity.setPos(x, y, z);
        entity.setYRot(newYRot);
        entity.setXRot(newXRot);
        steps--;
    }

    public void cancel() { steps = 0; }
    *///?}
}
