package dev.golem.core.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * How a living entity moves inside a {@link WorldFluids} fluid where the loader moves it by fluid type
 * (NeoForge, Forge). Fabric needs none of this: there the fluid's vanilla tag picks vanilla's own water or
 * lava movement, and an untagged fluid is air to the entity. These are vanilla's {@code travelInLava} and
 * {@code travelInAir}, so both loaders move an entity the same way.
 */
final class FluidMotion {

    private FluidMotion() {}

    /** Moves {@code entity} for one tick; false leaves it to the loader's water movement. */
    static boolean move(WorldFluids.Physics physics, LivingEntity entity, Vec3 input, double gravity, double fluidHeight) {
        switch (physics) {
            case LAVA -> lava(entity, input, gravity, fluidHeight);
            case NONE -> air(entity, input, gravity);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Vanilla {@code LivingEntity.travelInLava}. */
    private static void lava(LivingEntity entity, Vec3 input, double gravity, double fluidHeight) {
        boolean falling = entity.getDeltaMovement().y <= 0.0D;
        double oldY = entity.getY();
        entity.moveRelative(0.02F, input);
        entity.move(MoverType.SELF, entity.getDeltaMovement());
        if (fluidHeight <= entity.getFluidJumpThreshold()) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.5D, 0.8F, 0.5D));
            entity.setDeltaMovement(entity.getFluidFallingAdjustedMovement(gravity, falling, entity.getDeltaMovement()));
        } else {
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.5D));
        }
        if (gravity != 0.0D) entity.setDeltaMovement(entity.getDeltaMovement().add(0.0D, -gravity / 4.0D, 0.0D));
        Vec3 movement = entity.getDeltaMovement();
        if (entity.horizontalCollision && entity.isFree(movement.x, movement.y + 0.6F - entity.getY() + oldY, movement.z)) {
            entity.setDeltaMovement(movement.x, 0.3F, movement.z);
        }
    }

    /** Vanilla {@code LivingEntity.travelInAir}: 1.7 had no fluid physics for a custom liquid material. */
    private static void air(LivingEntity entity, Vec3 input, double gravity) {
        BlockPos below = BlockPos.containing(entity.getX(), entity.getBoundingBox().minY - 0.5000001D, entity.getZ());
        float blockFriction = entity.onGround() ? entity.level().getBlockState(below).getBlock().getFriction() : 1.0F;
        float friction = blockFriction * 0.91F;
        // vanilla handleRelativeFrictionAndCalculateMovement (private on 26.1), without its ladder handling
        float speed = entity.onGround() ? entity.getSpeed() * (0.21600002F / (blockFriction * blockFriction * blockFriction))
                : entity.isSprinting() ? 0.025999999F : 0.02F;
        entity.moveRelative(speed, input);
        entity.move(MoverType.SELF, entity.getDeltaMovement());
        Vec3 movement = entity.getDeltaMovement();
        double y = movement.y;
        MobEffectInstance levitation = entity.getEffect(MobEffects.LEVITATION);
        if (levitation != null) y += (0.05D * (levitation.getAmplifier() + 1) - movement.y) * 0.2D;
        else y -= gravity;
        entity.setDeltaMovement(movement.x * friction, y * 0.98F, movement.z * friction);
    }
}
