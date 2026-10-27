package dev.golem.core.world;

import java.util.function.UnaryOperator;
import net.minecraft.server.MinecraftServer;
//? if >=26.1 {
import net.minecraft.world.attribute.EnvironmentAttributes;
//?}

import java.util.OptionalDouble;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import dev.golem.core.hook.Hook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The sky as the old world read and changed it: the sun's and moon's positions across 26.1 moving both into
 * environment attributes, and runtime sky modifiers ({@link Modifier}).
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

    /**
     * Native server-side light-map input at a position. The raw environment factor exists from 26.1 onward;
     * older servers expose integer sky darkening, not the client's brightness input, so it is unavailable there.
     * Client rendering must use its camera attribute probe instead of this world-position sample.
     */
    public static OptionalDouble lightInput(Level level, BlockPos pos) {
        //? if >=26.1 {
        return OptionalDouble.of(level.environmentAttributes()
                .getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, pos));
        //?} else {
        /*return OptionalDouble.empty();
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
    // Sky modifiers

    /**
     * A runtime change to how bright and what colour the sky is - the stand-in for a 1.7 {@code WorldProvider}
     * override ({@code getSunBrightnessFactor}, {@code getSkyColor}, {@code getFogColor}, {@code drawClouds},
     * {@code getStarBrightness}, {@code getSunBrightness}, {@code calcSunriseSunsetColors}). Every method receives
     * the vanilla value and returns the one to use; colours are RGB in 0..1. Nothing is asked unless
     * {@link #active} says so for the level, so an inactive modifier leaves vanilla bit for bit.
     *
     * <p>How it is applied: on 26.1 each value is an environment attribute, and Golem Core appends one layer per
     * attribute after the level's default layers ({@code MixinEnvironmentSkyLayers}); below 26.1 the level's sky
     * darkening and the client level's colour getters are wrapped ({@code MixinLevelSkyDarken},
     * {@code MixinClientLevelSky}, {@code MixinDimensionEffectsSky}, {@code MixinOverworldFogColor}).
     */
    public interface Modifier {
        boolean active(Level level);

        /** 1.7 {@code getSunBrightnessFactor}: the 0..1 daylight behind the level's sky darkening (both sides). */
        default float daylight(Level level, float daylight) { return daylight; }

        /** The modern native sun angle in degrees, before daylight and rendering read it. */
        default float sunAngle(Level level, float angle) { return angle; }

        /** The current daytime query, independent of cached visual sky brightness (both sides). */
        default boolean brightOutside(Level level, boolean bright) { return bright; }

        /** Client: the sky colour. */
        default Vec3 skyColor(Level level, Vec3 color) { return color; }

        /** The same sky query at the sampled position, shared by environment layers and older client getters. */
        default Vec3 skyColor(Level level, Vec3 color, Vec3 position, float partialTick) {
            return skyColor(level, color);
        }

        /** Client: the brightness-dependent fog colour, before vanilla blends the sky into it. */
        default Vec3 fogColor(Level level, Vec3 color) { return color; }

        default Vec3 fogColor(Level level, Vec3 color, Vec3 position, float partialTick) {
            return fogColor(level, color);
        }

        /** Client: the cloud colour. */
        default Vec3 cloudColor(Level level, Vec3 color) { return color; }

        /** Modern cloud colour includes visibility in its alpha channel. */
        default int cloudColor(Level level, int color) {
            return Skies.argb(color, value -> cloudColor(level, value));
        }

        /** Client: the stars' brightness. */
        default float starBrightness(Level level, float brightness) { return brightness; }

        /** Client: the sky's contribution to the light map (1.7 {@code getSunBrightness}; 0.2 at night, 1 at noon). */
        default float skyLight(Level level, float factor) { return factor; }

        /** Whether modern light maps consume this level's factor directly rather than as classic sun brightness. */
        default boolean rawSkyLightFactor(Level level) { return false; }

        /** The modern factor query; older client getters continue to use {@link #skyLight}. */
        default float skyLightFactor(Level level, float factor) {
            return skyLight(level, (factor - 0.05F) / 0.95F) * 0.95F + 0.05F;
        }

        /** Client: the sunrise/sunset glow as RGBA, or null when there is none. */
        default float @Nullable [] sunriseColor(Level level, float @Nullable [] rgba) { return rgba; }
    }

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final Hook<Modifier> MODIFIERS = Hook.notifying();

    public static void addModifier(Modifier modifier) {
        MODIFIERS.register(modifier);
    }

    /** True when any modifier wants to change {@code level}'s sky. Called by the hooks before anything else. */
    public static boolean active(Level level) {
        if (MODIFIERS.isEmpty() || level == null) return false;
        for (Modifier modifier : MODIFIERS) if (modifier.active(level)) return true;
        return false;
    }

    public static float daylight(Level level, float value) {
        for (Modifier m : MODIFIERS) if (m.active(level)) value = m.daylight(level, value);
        return value;
    }

    public static float sunAngle(Level level, float value) {
        for (Modifier m : MODIFIERS) if (m.active(level)) value = m.sunAngle(level, value);
        return value;
    }

    public static boolean brightOutside(Level level, boolean value) {
        for (Modifier m : MODIFIERS) if (m.active(level)) value = m.brightOutside(level, value);
        return value;
    }

    public static Vec3 skyColor(Level level, Vec3 value) {
        for (Modifier m : MODIFIERS) if (m.active(level)) value = m.skyColor(level, value);
        return value;
    }

    public static Vec3 skyColor(Level level, Vec3 value, Vec3 position, float partialTick) {
        for (var modifier : MODIFIERS) if (modifier.active(level)) value = modifier.skyColor(level, value, position, partialTick);
        return value;
    }

    public static Vec3 fogColor(Level level, Vec3 value) {
        for (Modifier m : MODIFIERS) if (m.active(level)) value = m.fogColor(level, value);
        return value;
    }

    public static Vec3 fogColor(Level level, Vec3 value, Vec3 position, float partialTick) {
        for (var modifier : MODIFIERS) if (modifier.active(level)) value = modifier.fogColor(level, value, position, partialTick);
        return value;
    }

    public static Vec3 cloudColor(Level level, Vec3 value) {
        for (Modifier m : MODIFIERS) if (m.active(level)) value = m.cloudColor(level, value);
        return value;
    }

    public static int cloudColorArgb(Level level, int value) {
        for (var modifier : MODIFIERS) if (modifier.active(level)) value = modifier.cloudColor(level, value);
        return value;
    }

    public static boolean rawSkyLightFactor(Level level) {
        //? if >=26.1 {
        for (var modifier : MODIFIERS) if (modifier.active(level) && modifier.rawSkyLightFactor(level)) return true;
        //?}
        return false;
    }

    public static float starBrightness(Level level, float value) {
        for (Modifier m : MODIFIERS) if (m.active(level)) value = m.starBrightness(level, value);
        return value;
    }

    public static float skyLight(Level level, float value) {
        for (Modifier m : MODIFIERS) if (m.active(level)) value = m.skyLight(level, value);
        return value;
    }

    /**
     * The client level's sky light after the modifiers ({@link Modifier#skyLight}, 1.7's {@code getSunBrightness}) as
     * last computed for its light map, or NaN before any. 26.1 only: its light map reads the factor and colour as two
     * attributes, and {@link dev.golem.core.client.Lightmaps}' classic formula needs the brightness behind both.
     */
    public static volatile float clientSkyLight = Float.NaN;

    /**
     * 26.1's {@code visual/sky_light_factor} for a level with an active modifier. The attribute is the light map's
     * multiplier, which pre-26.1 light maps derive from the sun brightness as {@code f * 0.95 + 0.05} (the overworld
     * timeline's 1 by day and 0.24 by night are exactly that of 1.7's 1 and 0.2). The modifiers speak in the sun
     * brightness on every node, so the vanilla factor is turned back into one first, and the result into a factor again.
     */
    public static float skyLightFactor(Level level, float factor) {
        if (rawSkyLightFactor(level)) {
            for (var modifier : MODIFIERS) if (modifier.active(level)) factor = modifier.skyLightFactor(level, factor);
            if (level.isClientSide()) clientSkyLight = Float.NaN;
            return Mth.clamp(factor, 0.0F, 1.0F);
        }
        float sun = skyLight(level, (factor - 0.05F) / 0.95F);
        if (level.isClientSide()) clientSkyLight = sun;
        return Mth.clamp(sun * 0.95F + 0.05F, 0.0F, 1.0F);
    }

    /**
     * 26.1's {@code visual/sky_light_color} for a level with an active modifier: pre-26.1's (and 1.7's) sky light colour
     * from the sun brightness, {@code (f, f, 1)} lerped 35 % to white - the overworld timeline's white by day and
     * {@code 0.48, 0.48, 1} by night are exactly that of 1 and 0.2. Uses the brightness {@link #skyLightFactor} just
     * computed (26.1 reads the factor first); the vanilla colour when there is none yet.
     */
    public static int skyLightColor(Level level, int argb) {
        if (rawSkyLightFactor(level)) return argb;
        float sun = clientSkyLight;
        if (!level.isClientSide() || Float.isNaN(sun)) return argb;
        float rg = Mth.clamp(sun + (1.0F - sun) * 0.35F, 0.0F, 1.0F);
        int c = Math.round(rg * 255.0F);
        return (argb & 0xFF000000) | c << 16 | c << 8 | 0xFF;
    }

    /**
     * Refreshes every level's sky after the time rate changed (the Time Wand): 26.1 caches environment attributes per
     * tick and the sky brightness, so both are invalidated; older nodes derive both live. No-op below 26.1.
     */
    public static void refreshAfterRateChange(MinecraftServer server) {
        //? if >=26.1 {
        for (var level : server.getAllLevels()) {
            level.environmentAttributes().invalidateTickCache();
            level.updateSkyBrightness();
        }
        //?}
    }

    public static float @Nullable [] sunriseColor(Level level, float @Nullable [] value) {
        for (Modifier m : MODIFIERS) if (m.active(level)) value = m.sunriseColor(level, value);
        return value;
    }

    /** An ARGB int through an RGB modifier, alpha kept. For the 26.1 colour attributes. */
    public static int argb(int argb, UnaryOperator<Vec3> op) {
        Vec3 rgb = new Vec3(((argb >> 16) & 0xFF) / 255.0,
                ((argb >> 8) & 0xFF) / 255.0, (argb & 0xFF) / 255.0);
        Vec3 out = op.apply(rgb);
        return (argb & 0xFF000000) | (channel(out.x) << 16) | (channel(out.y) << 8) | channel(out.z);
    }

    /** The sunrise ARGB int through {@link #sunriseColor}; 0 alpha means no glow. For the 26.1 attribute. */
    public static int sunriseArgb(Level level, int argb) {
        float[] rgba = {((argb >> 16) & 0xFF) / 255F, ((argb >> 8) & 0xFF) / 255F, (argb & 0xFF) / 255F,
                ((argb >>> 24) & 0xFF) / 255F};
        float[] out = sunriseColor(level, rgba);
        if (out == null) return 0;
        return (channel(out[3]) << 24) | (channel(out[0]) << 16) | (channel(out[1]) << 8) | channel(out[2]);
    }

    private static int channel(double v) {
        return Math.max(0, Math.min(255, (int) Math.round(v * 255.0)));
    }
}
