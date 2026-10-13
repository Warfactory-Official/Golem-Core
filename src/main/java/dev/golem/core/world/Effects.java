package dev.golem.core.world;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Cross-version status-effect construction and entity ignition. */
public final class Effects {

    private Effects() {}

    /**
     * The strength effect, whatever this version calls it.
     *
     * <p>Vanilla renamed the field from {@code DAMAGE_BOOST} to {@code STRENGTH} at 1.21.2. Typed as
     * {@link Object} to match {@link #instance} and {@link #add}, which take the effect loosely because
     * it is a raw {@code MobEffect} below 1.21 and a {@code Holder} above it.
     */
    public static Object strength() {
        //? if >=1.21.2 {
        return net.minecraft.world.effect.MobEffects.STRENGTH;
        //?} else {
        /*return net.minecraft.world.effect.MobEffects.DAMAGE_BOOST;
        *///?}
    }

    public static Object resistance() {
        //? if >=1.21.2 {
        return net.minecraft.world.effect.MobEffects.RESISTANCE;
        //?} else {
        /*return net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE;
        *///?}
    }

    public static Object speed() {
        //? if >=1.21.2 {
        return net.minecraft.world.effect.MobEffects.SPEED;
        //?} else {
        /*return net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED;
        *///?}
    }

    public static Object haste() {
        //? if >=1.21.2 {
        return net.minecraft.world.effect.MobEffects.HASTE;
        //?} else {
        /*return net.minecraft.world.effect.MobEffects.DIG_SPEED;
        *///?}
    }

    public static Object jumpBoost() {
        //? if >=1.21.2 {
        return net.minecraft.world.effect.MobEffects.JUMP_BOOST;
        //?} else {
        /*return net.minecraft.world.effect.MobEffects.JUMP;
        *///?}
    }

    /** Instant health: {@code INSTANT_HEALTH} on 1.21.2 and later, {@code HEAL} before. */
    public static Object instantHealth() {
        //? if >=1.21.2 {
        return net.minecraft.world.effect.MobEffects.INSTANT_HEALTH;
        //?} else {
        /*return net.minecraft.world.effect.MobEffects.HEAL;
        *///?}
    }

    public static MobEffectInstance instance(Object effect, int duration, int amplifier) {
        //? if >=1.21 {
        return new MobEffectInstance((net.minecraft.core.Holder<MobEffect>) effect, duration, amplifier);
        //?} else {
        /*return new MobEffectInstance((MobEffect) effect, duration, amplifier);
        *///?}
    }

    /** Constructs an instance from a concrete registered effect, wrapping it on holder-based versions. */
    public static MobEffectInstance instanceRegistered(MobEffect effect, int duration, int amplifier) {
        //? if >=1.21 {
        return instance(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                duration, amplifier);
        //?} else {
        /*return instance(effect, duration, amplifier);
        *///?}
    }

    /**
     * An effect instance with explicit ambient/visible flags.
     *
     * <p>{@code visible = false} suppresses the swirling particles, which an effect re-applied every
     * tick (a full-set armour bonus, say) needs.
     */
    /** {@link #instanceRegistered(MobEffect, int, int)} with the ambient/visible flags. */
    public static MobEffectInstance instanceRegistered(MobEffect effect, int duration, int amplifier,
            boolean ambient, boolean visible) {
        //? if >=1.21 {
        return instance(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                duration, amplifier, ambient, visible);
        //?} else {
        /*return instance(effect, duration, amplifier, ambient, visible);
        *///?}
    }

    public static MobEffectInstance instance(Object effect, int duration, int amplifier, boolean ambient,
            boolean visible) {
        return instance(effect, duration, amplifier, ambient, visible, true);
    }

    /**
     * An effect instance with explicit ambient/visible/icon flags.
     *
     * <p><b>The {@code showIcon} split is a porting hazard.</b> 1.12's five-argument
     * {@code PotionEffect(potion, duration, amplifier, ambient, showParticles)} controlled
     * <i>particles only</i> and always showed the status icon. Modern Minecraft's five-argument
     * constructor sets {@code showIcon = visible}, so a literal transcription hides the icon too, and
     * the effect looks never applied. Pass {@code visible = false, showIcon = true} to reproduce 1.12.
     */
    public static MobEffectInstance instance(Object effect, int duration, int amplifier, boolean ambient,
            boolean visible, boolean showIcon) {
        //? if >=1.21 {
        return new MobEffectInstance((net.minecraft.core.Holder<MobEffect>) effect, duration, amplifier,
                ambient, visible, showIcon);
        //?} else {
        /*return new MobEffectInstance((MobEffect) effect, duration, amplifier, ambient, visible, showIcon);
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

    public static boolean has(LivingEntity entity, Object effect) {
        //? if >=1.21 {
        return entity.hasEffect((net.minecraft.core.Holder<MobEffect>) effect);
        //?} else {
        /*return entity.hasEffect((MobEffect) effect);
        *///?}
    }

    public static MobEffectInstance get(LivingEntity entity, Object effect) {
        //? if >=1.21 {
        return entity.getEffect((net.minecraft.core.Holder<MobEffect>) effect);
        //?} else {
        /*return entity.getEffect((MobEffect) effect);
        *///?}
    }

    public static void remove(LivingEntity entity, Object effect) {
        //? if >=1.21 {
        entity.removeEffect((net.minecraft.core.Holder<MobEffect>) effect);
        //?} else {
        /*entity.removeEffect((MobEffect) effect);
        *///?}
    }

    public static boolean hasRegistered(LivingEntity entity, MobEffect effect) {
        //? if >=1.21 {
        return has(entity, net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
        //?} else {
        /*return has(entity, effect);
        *///?}
    }

    public static MobEffectInstance getRegistered(LivingEntity entity, MobEffect effect) {
        //? if >=1.21 {
        return get(entity, net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
        //?} else {
        /*return get(entity, effect);
        *///?}
    }

    public static void removeRegistered(LivingEntity entity, MobEffect effect) {
        //? if >=1.21 {
        entity.removeEffect(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
        //?} else {
        /*entity.removeEffect(effect);
        *///?}
    }

    /** Whether an active effect is harmful. Mirrors legacy {@code Potion.isBadEffect}. */
    public static boolean isHarmful(MobEffectInstance instance) {
        //? if >=1.21 {
        return instance.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL;
        //?} else {
        /*return instance.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL;
        *///?}
    }

    /** The effect kind of an active instance, typed loosely to pair with {@link #remove}. */
    public static Object kind(MobEffectInstance instance) {
        return instance.getEffect();
    }

    /** The registry id of {@code instance}'s effect, {@code "minecraft:wither"}. */
    public static String id(MobEffectInstance instance) {
        //? if >=1.21 {
        return String.valueOf(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value()));
        //?} else {
        /*return String.valueOf(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect()));
        *///?}
    }

    /** An instance of the effect registered as {@code id}; fails if there is none. */
    public static MobEffectInstance instance(String id, int duration, int amplifier) {
        MobEffect effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getOptional(dev.golem.core.Ids.parse(id))
                .orElseThrow(() -> new IllegalArgumentException("no effect " + id));
        return instanceRegistered(effect, duration, amplifier);
    }

    public static void clear(LivingEntity entity) {
        entity.removeAllEffects();
    }

    public static void ignite(Entity entity, int seconds) {
        //? if >=1.21 {
        entity.igniteForSeconds(seconds);
        //?} else {
        /*entity.setSecondsOnFire(seconds);
        *///?}
    }


    /** {@link #ignite(Entity, int)} for the 26.1 float spelling. */
    public static void ignite(Entity entity, float seconds) {
        ignite(entity, (int) Math.ceil(seconds));
    }
}
