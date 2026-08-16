package dev.golem.core.world;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Cross-version status-effect construction and entity ignition. */
public final class Effects {

    private Effects() {}

    public static MobEffectInstance instance(Object effect, int duration, int amplifier) {
        //? if >=1.21 {
        return new MobEffectInstance((net.minecraft.core.Holder<MobEffect>) effect, duration, amplifier);
        //?} else {
        /*return new MobEffectInstance((MobEffect) effect, duration, amplifier);
        *///?}
    }

    /** Adds a concrete effect regardless of whether the target uses raw effects or registry holders. */
    public static void add(LivingEntity entity, Object effect, int duration, int amplifier) {
        if (amplifier >= 0) entity.addEffect(instance(effect, duration, amplifier));
    }

    public static void addHolder(LivingEntity entity,
                           //? if >=1.20.5 {
                           net.minecraft.core.Holder<MobEffect> effect,
                           //?} else {
                           /*MobEffect effect,
                           *///?}
                           int duration, int amplifier) {
        if (amplifier >= 0) entity.addEffect(new MobEffectInstance(effect, duration, amplifier));
    }

    /** Adds an effect returned directly from a mod registry object. */
    public static void addRegistered(LivingEntity entity, MobEffect effect, int duration, int amplifier) {
        //? if >=1.21 {
        add(entity, net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), duration, amplifier);
        //?} else {
        /*add(entity, effect, duration, amplifier);
        *///?}
    }

    public static void ignite(Entity entity, int seconds) {
        //? if >=1.21 {
        entity.igniteForSeconds(seconds);
        //?} else {
        /*entity.setSecondsOnFire(seconds);
        *///?}
    }
}
