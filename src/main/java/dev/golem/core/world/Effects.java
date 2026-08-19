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
     * <p>Vanilla renamed the field from {@code DAMAGE_BOOST} to {@code STRENGTH} at 1.21.2 — a pure
     * spelling change, so it belongs here rather than in a guard at every use site. Typed as
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

    public static MobEffectInstance instance(Object effect, int duration, int amplifier) {
        //? if >=1.21 {
        return new MobEffectInstance((net.minecraft.core.Holder<MobEffect>) effect, duration, amplifier);
        //?} else {
        /*return new MobEffectInstance((MobEffect) effect, duration, amplifier);
        *///?}
    }

    /**
     * An effect instance with explicit ambient/visible flags.
     *
     * <p>{@code visible = false} is the interesting one: it suppresses the swirling particles, which is
     * what an effect re-applied every tick — a full-set armour bonus, say — needs if it is not to bury
     * the wearer in them.
     */
    public static MobEffectInstance instance(Object effect, int duration, int amplifier, boolean ambient,
            boolean visible) {
        return instance(effect, duration, amplifier, ambient, visible, true);
    }

    /**
     * An effect instance with explicit ambient/visible/icon flags.
     *
     * <p><b>The {@code showIcon} split is a real porting hazard.</b> 1.12's five-argument
     * {@code PotionEffect(potion, duration, amplifier, ambient, showParticles)} controlled
     * <i>particles only</i> and always showed the status icon. Modern Minecraft's five-argument
     * constructor sets {@code showIcon = visible}, so the literal transcription of that call silently
     * hides the icon too — and an effect with no particles and no icon looks, to a player, exactly like
     * an effect that was never applied. Pass {@code visible = false, showIcon = true} to reproduce 1.12.
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

    public static void ignite(Entity entity, int seconds) {
        //? if >=1.21 {
        entity.igniteForSeconds(seconds);
        //?} else {
        /*entity.setSecondsOnFire(seconds);
        *///?}
    }
}
