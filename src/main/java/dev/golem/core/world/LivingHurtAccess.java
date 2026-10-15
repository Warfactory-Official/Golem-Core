package dev.golem.core.world;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * {@code LivingEntity.lastHurt}, the damage taken during the current invulnerability window, which vanilla
 * keeps protected on every version. HBM's bullets add it back so a second hit inside the window still
 * lands its full amount (1.7 {@code EntityDamageUtil} retry). Also the protected {@code getHurtSound}, which
 * HBM 1.12's gluon gun played on the client for every hit.
 */
public interface LivingHurtAccess {

    float golem$lastHurt();

    /** 26.1 {@code getLastHurtByPlayer()}; the protected field before it. */
    @Nullable Player golem$lastHurtByPlayer();

    /** {@code getHurtSound(source)}: the sound this entity makes when hurt by {@code source}, or null. */
    @Nullable SoundEvent golem$hurtSound(DamageSource source);
}
