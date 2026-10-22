package dev.golem.core.render;

import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mods that pose a humanoid's skeleton themselves (Mo' Bends): they write the pose into the model's parts before
 * the layers draw, so vanilla's crouch pose is not on the model ({@link HumanoidPose#sneaking} is false). Client-only.
 */
public final class BodyAnimations {

    private BodyAnimations() {}

    private static Predicate<LivingEntity> animated = entity -> false;

    public static void install(Predicate<LivingEntity> test) {
        animated = test;
    }

    public static boolean animates(LivingEntity entity) {
        return animated.test(entity);
    }
}
