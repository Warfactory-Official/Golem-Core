package dev.golem.core.mixin;

import dev.golem.core.world.LivingHurtAccess;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes {@link LivingEntity#lastHurt} and {@code getHurtSound}; see {@link LivingHurtAccess}.
 *
 * <p>{@code getHurtSound} is a shadow, not an access widener: widening a protected method makes it public, and every
 * subclass that overrides it as protected (vanilla mobs, HBM's own) would then no longer compile.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingHurtAccess implements LivingHurtAccess {

    @Shadow
    protected float lastHurt;

    @Override
    public float golem$lastHurt() {
        return lastHurt;
    }

    @Shadow
    protected abstract @Nullable SoundEvent getHurtSound(
            DamageSource source);

    @Override
    public @Nullable SoundEvent golem$hurtSound(
            DamageSource source) {
        return getHurtSound(source);
    }

    //? if >=26.1 {
    @Shadow
    public abstract @Nullable Player getLastHurtByPlayer();

    @Override
    public @Nullable Player golem$lastHurtByPlayer() {
        return getLastHurtByPlayer();
    }
    //?} else {
    /*@Shadow
    protected @Nullable Player lastHurtByPlayer;

    @Override
    public @Nullable Player golem$lastHurtByPlayer() {
        return lastHurtByPlayer;
    }
    *///?}
}
