package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * The three living-entity physics attributes vanilla added in 1.20.5, on every node: gravity (0.08 blocks per tick
 * squared), safe fall distance (3 blocks) and fall damage multiplier (1). From 1.20.5 they are vanilla's own
 * ({@code Attributes.GRAVITY}, {@code SAFE_FALL_DISTANCE}, {@code FALL_DAMAGE_MULTIPLIER}). On 1.20.1 Golem Core
 * backports them: Forge's {@code ENTITY_GRAVITY} is the gravity, and Golem Core registers the other two (and, on
 * Fabric, the gravity) as {@code golemcore:generic.*}, adds them to every living entity and applies them where 1.20.5
 * reads them ({@code MixinLegacyLivingPhysics}).
 *
 * <p>Change them with modifiers ({@link Attributes#setTransient}), never the base value, so that several mods compose.
 */
public final class Physics {

    private Physics() {}

    @FunctionalInterface
    public interface EffectiveGravity {
        double gravity(LivingEntity entity, double gravity);
    }

    /** Transforms the pull read by living travel; modern vanilla applies Slow Falling after this read. */
    public static final Hook<EffectiveGravity> EFFECTIVE_GRAVITY = Hook.of(listeners -> (entity, gravity) -> {
        for (var listener : listeners) gravity = listener.gravity(entity, gravity);
        return gravity;
    });

    @FunctionalInterface
    public interface VerticalAirDrag {
        double drag(LivingEntity entity, double vertical, double horizontal);
    }

    /** The vertical air drag and vanilla's already-computed horizontal drag, including ground friction. */
    public static final Hook<VerticalAirDrag> VERTICAL_AIR_DRAG = Hook.of(listeners -> (entity, vertical, horizontal) -> {
        for (var listener : listeners) vertical = listener.drag(entity, vertical, horizontal);
        return vertical;
    });

    public static double effectiveGravity(LivingEntity entity, double gravity) {
        return EFFECTIVE_GRAVITY.invoker().gravity(entity, gravity);
    }

    public static double verticalAirDrag(LivingEntity entity, double vertical, double horizontal) {
        return VERTICAL_AIR_DRAG.invoker().drag(entity, vertical, horizontal);
    }

    /** The jump flag used by living travel, including an airborne player's held jump input. */
    public static boolean isJumping(LivingEntity entity) {
        return entity.jumping;
    }

    /** Golem Core's entrypoints call this first: on 1.20.1 the backported attributes' register must exist early. */
    public static void init() {
        //? if <1.20.5 {
        /*LegacyPhysics.init();
        *///?}
    }

    public static AttributeInstance gravity(LivingEntity entity) {
        //? if >=1.20.5 {
        return entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
        //?} elif forge {
        /*return entity.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get());
        *///?} else {
        /*return entity.getAttribute(LegacyPhysics.GRAVITY.get());
        *///?}
    }

    public static AttributeInstance safeFallDistance(LivingEntity entity) {
        //? if >=1.20.5 {
        return entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE);
        //?} else {
        /*return entity.getAttribute(LegacyPhysics.SAFE_FALL_DISTANCE.get());
        *///?}
    }

    public static AttributeInstance fallDamageMultiplier(LivingEntity entity) {
        //? if >=1.20.5 {
        return entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FALL_DAMAGE_MULTIPLIER);
        //?} else {
        /*return entity.getAttribute(LegacyPhysics.FALL_DAMAGE_MULTIPLIER.get());
        *///?}
    }
}
