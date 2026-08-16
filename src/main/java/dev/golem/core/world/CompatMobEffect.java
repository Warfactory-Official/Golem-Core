package dev.golem.core.world;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** A MobEffect with a stable per-tick callback across the server-level signature change. */
public abstract class CompatMobEffect extends MobEffect {

    protected CompatMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    //? if >=26.1 {
    public final boolean applyEffectTick(net.minecraft.server.level.ServerLevel level, LivingEntity entity, int amplifier) {
    //?} elif >=1.21 {
    /*public final boolean applyEffectTick(LivingEntity entity, int amplifier) {
    *///?} else {
    /*public final void applyEffectTick(LivingEntity entity, int amplifier) {
    *///?}
        boolean applied = applyCompatEffectTick(entity, amplifier);
        //? if >=1.21 {
        return applied;
        //?}
    }

    protected abstract boolean applyCompatEffectTick(LivingEntity entity, int amplifier);

    /**
     * Whether {@link #applyCompatEffectTick} runs on this tick. Override to change the cadence — an
     * effect that acts every tick returns {@code true} unconditionally.
     *
     * <p>This exists for the same reason as the tick callback above: 1.21 renamed the vanilla hook
     * from {@code isDurationEffectTick} to {@code shouldApplyEffectTickThisTick}. Leaving it out is
     * what forced consumers to hand-guard one method of a class whose sibling was already abstracted.
     */
    protected boolean shouldApplyTick(int duration, int amplifier) {
        //? if >=1.21 {
        return super.shouldApplyEffectTickThisTick(duration, amplifier);
        //?} else {
        /*return super.isDurationEffectTick(duration, amplifier);
        *///?}
    }

    @Override
    //? if >=1.21 {
    public final boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    //?} else {
    /*public final boolean isDurationEffectTick(int duration, int amplifier) {
    *///?}
        return shouldApplyTick(duration, amplifier);
    }
}
