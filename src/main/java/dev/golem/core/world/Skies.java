package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The sun's and moon's positions as the old world read them, across 26.1 moving both into environment
 * attributes.
 *
 * <p>{@link #celestialAngle} is 1.7's {@code getCelestialAngle}: 0 at noon, 0.5 at midnight, wrapping at 1.
 * Before 26.1 that is the dimension's {@code timeOfDay}; from 26.1 it is the {@code visual/sun_angle}
 * attribute, which the overworld timeline keys to the same curve in degrees. {@link #moonPhase} is 0 at full
 * moon and 4 at new moon, 1.7's {@code getMoonPhase} indexing.
 */
public final class Skies {

    private Skies() {}

    public static float celestialAngle(Level level, BlockPos pos) {
        //? if >=26.1 {
        return level.environmentAttributes()
                .getValue(net.minecraft.world.attribute.EnvironmentAttributes.SUN_ANGLE, pos) / 360.0F;
        //?} else {
        /*return level.getTimeOfDay(0.0F);
        *///?}
    }

    public static int moonPhase(Level level, BlockPos pos) {
        //? if >=26.1 {
        return level.environmentAttributes()
                .getValue(net.minecraft.world.attribute.EnvironmentAttributes.MOON_PHASE, pos).index();
        //?} else {
        /*return level.getMoonPhase();
        *///?}
    }
}
