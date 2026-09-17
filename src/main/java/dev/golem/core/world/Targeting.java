package dev.golem.core.world;

import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Target-goal filters. 1.21.2 replaced the {@code Predicate<LivingEntity>} the nearest-attackable goals
 * took with {@code TargetingConditions.Selector} (entity plus level); a consumer supplies the predicate
 * and never names either type.
 */
public final class Targeting {

    private Targeting() {}

    //? if >=26.1 {
    public static net.minecraft.world.entity.ai.targeting.TargetingConditions.@Nullable Selector selector(
            @Nullable Predicate<LivingEntity> predicate) {
        return predicate == null ? null : (entity, level) -> predicate.test(entity);
    }
    //?} else {
    /*public static @Nullable Predicate<LivingEntity> selector(@Nullable Predicate<LivingEntity> predicate) {
        return predicate;
    }
    *///?}
}
