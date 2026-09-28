package dev.golem.core.world;

import net.minecraft.world.entity.LivingEntity;

/**
 * An entity's step height as an absolute override that can be cleanly withdrawn.
 *
 * <p>The legacy mods wrote {@code Entity.stepHeight} directly and restored it by recognising their own
 * value. From 1.20.5 the height is the {@code STEP_HEIGHT} attribute, so an override is an additive
 * transient modifier sized to reach the requested height from the attribute's base; 1.20.1 still has the
 * plain {@code maxUpStep} field behind {@code setMaxUpStep}, restored to the value it replaced.
 */
public final class StepHeight {

    private StepHeight() {}

    //? if <1.20.5 {
    /*// One map per side: Entity.equals compares entity ids, and in singleplayer the integrated server's
    // player and the client's player share one, so a single weak map would mix the two sides' values.
    private static final java.util.Map<LivingEntity, Float> PREVIOUS_SERVER = new java.util.WeakHashMap<>();
    private static final java.util.Map<LivingEntity, Float> PREVIOUS_CLIENT = new java.util.WeakHashMap<>();

    private static java.util.Map<LivingEntity, Float> previous(LivingEntity entity) {
        return entity.level().isClientSide() ? PREVIOUS_CLIENT : PREVIOUS_SERVER;
    }
    *///?}

    /** Makes {@code entity} step up exactly {@code height} blocks until {@link #clear} with the same id. */
    public static void set(LivingEntity entity, String id, double height) {
        //? if >=1.20.5 {
        var attribute = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
        if (attribute == null) return;
        Attributes.setTransient(attribute, id, height - attribute.getBaseValue(), Attributes.Op.ADD);
        //?} else {
        /*if (!previous(entity).containsKey(entity)) previous(entity).put(entity, entity.maxUpStep());
        if (entity.maxUpStep() != (float) height) entity.setMaxUpStep((float) height);
        *///?}
    }

    /** Withdraws an override made by {@link #set}; a no-op when none is active. */
    public static void clear(LivingEntity entity, String id) {
        //? if >=1.20.5 {
        var attribute = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
        if (attribute != null && Attributes.has(attribute, id)) Attributes.removeTransient(attribute, id);
        //?} else {
        /*Float previous = previous(entity).remove(entity);
        if (previous != null) entity.setMaxUpStep(previous);
        *///?}
    }

    /** The height the entity currently steps up. */
    public static double get(LivingEntity entity) {
        //? if >=1.20.5 {
        var attribute = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
        return attribute == null ? entity.maxUpStep() : attribute.getValue();
        //?} else {
        /*return entity.maxUpStep();
        *///?}
    }
}
