package dev.golem.core.mixin;

//? if >=26.1 {
import dev.golem.core.world.Skies;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1's half of {@link Skies#addModifier}: one time-based layer per sky attribute, appended after the level's
 * default layers (dimension, biome, timelines, weather) on both the server and the client level.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) the attribute system is built inside {@code ServerLevel}/{@code
 * ClientLevel} construction with no vanilla extension point; (2) NeoForge 26.1 and Fabric offer no event to add an
 * environment attribute layer; (3) widening the builder would still need a call at construction time. The inject
 * is one TAIL call on {@code addDefaultLayers}. The layers return their input unchanged unless a modifier is active
 * for the level, so vanilla values are untouched otherwise.
 *
 * <p>{@code SKY_LIGHT_LEVEL} (the level's sky darkening, {@code skyDarken = 15 - level}) is mapped onto 1.7's
 * daylight factor as {@code (level - 4) / 11}: 4 is the overworld's darkest sky light, 11 its range, which is the
 * pre-26.1 {@code skyDarken = (1 - daylight) * 11}.
 */
@Mixin(EnvironmentAttributeSystem.class)
public abstract class MixinEnvironmentSkyLayers {

    @Inject(method = "addDefaultLayers(Lnet/minecraft/world/attribute/EnvironmentAttributeSystem$Builder;Lnet/minecraft/world/level/Level;)V",
            at = @At("TAIL"))
    private static void golem$skyModifiers(EnvironmentAttributeSystem.Builder builder, Level level, CallbackInfo ci) {
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_LEVEL, (value, tick) -> {
            if (!Skies.active(level)) return value;
            float daylight = (value - 4F) / 11F;
            return 4F + 11F * Skies.daylight(level, daylight);
        });
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (value, tick) ->
                Skies.active(level) ? Skies.argb(value, c -> Skies.skyColor(level, c)) : value);
        builder.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (value, tick) ->
                Skies.active(level) ? Skies.argb(value, c -> Skies.fogColor(level, c)) : value);
        builder.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (value, tick) ->
                Skies.active(level) ? Skies.argb(value, c -> Skies.cloudColor(level, c)) : value);
        builder.addTimeBasedLayer(EnvironmentAttributes.STAR_BRIGHTNESS, (value, tick) ->
                Skies.active(level) ? Skies.starBrightness(level, value) : value);
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_FACTOR, (value, tick) ->
                Skies.active(level) ? Skies.skyLight(level, value) : value);
        builder.addTimeBasedLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, (value, tick) ->
                Skies.active(level) ? Skies.sunriseArgb(level, value) : value);
    }
}
//?} else {
/*public final class MixinEnvironmentSkyLayers {
    // Empty below 26.1, where there are no environment attributes; see MixinLevelSkyDarken and MixinClientLevelSky.
    private MixinEnvironmentSkyLayers() {}
}
*///?}
