package dev.golem.core.world;

/**
 * {@code LivingEntity.lastHurt}, the damage taken during the current invulnerability window, which vanilla
 * keeps protected on every version. HBM's bullets add it back so a second hit inside the window still
 * lands its full amount (1.7 {@code EntityDamageUtil} retry).
 */
public interface LivingHurtAccess {

    float golem$lastHurt();

    /** 26.1 {@code getLastHurtByPlayer()}; the protected field before it. */
    net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player golem$lastHurtByPlayer();
}
