package dev.golem.core.world;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * {@link MeleeAttackGoal} with the reach older versions use: twice the attacker's width, squared, plus the target's
 * width. 1.21 shrank it to a hitbox inflated by 0.83, so a wide mob stops pathing and never reaches its target.
 * A subclass states another reach through {@link #inReach}.
 */
public class CompatMeleeAttackGoal extends MeleeAttackGoal {

    public CompatMeleeAttackGoal(PathfinderMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(mob, speedModifier, followingTargetEvenIfNotSeen);
    }

    /** Whether {@code target} is close enough to hit. */
    protected boolean inReach(LivingEntity target) {
        double reach = mob.getBbWidth() * 2.0F * mob.getBbWidth() * 2.0F + target.getBbWidth();
        return mob.distanceToSqr(target.getX(), target.getY(), target.getZ()) <= reach;
    }

    //? if >=1.21 {
    @Override
    protected boolean canPerformAttack(LivingEntity target) {
        return isTimeToAttack() && inReach(target) && mob.getSensing().hasLineOfSight(target);
    }
    //?} else {
    /*@Override
    protected void checkAndPerformAttack(LivingEntity target, double distanceSqr) {
        if (inReach(target) && isTimeToAttack()) {
            resetAttackCooldown();
            mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
        }
    }
    *///?}
}
