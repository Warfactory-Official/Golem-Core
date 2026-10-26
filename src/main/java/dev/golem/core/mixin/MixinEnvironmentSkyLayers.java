package dev.golem.core.mixin;

import dev.golem.core.client.Clouds;

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
 * <p>Construction constraints: (1) the attribute system is built inside {@code ServerLevel}/{@code
 * ClientLevel} construction with no vanilla extension point; (2) NeoForge 26.1 and Fabric offer no event to add an
 * environment attribute layer; (3) widening the builder would still need a call at construction time. The inject
 * is one TAIL call on {@code addDefaultLayers}. The layers return their input unchanged unless a modifier is active
 * for the level, so vanilla values are untouched otherwise.
 *
 * <p>{@code SKY_LIGHT_LEVEL} (the level's sky darkening, {@code skyDarken = 15 - level}) is mapped onto 1.7's
 * daylight factor as {@code (level - 4) / 11}: 4 is the overworld's darkest sky light, 11 its range, which is the
 * pre-26.1 {@code skyDarken = (1 - daylight) * 11}.
 *
 * <p>The same TAIL also serves {@link dev.golem.core.client.Clouds}: a cloud-colour layer that opens or closes
 * vanilla's cloud pass for a registered level, and on NeoForge a {@code neoforge:visual/custom_clouds} layer that
 * selects Golem's {@code CustomCloudsRenderer}. A shared dimension type cannot carry that NeoForge-only attribute
 * (Fabric rejects the file), so a layer is the only place to set it. Both return their input for every other level.
 * {@link dev.golem.core.client.Precipitation} and {@code neoforge:visual/custom_weather_effects} work the same way.
 */
@Mixin(EnvironmentAttributeSystem.class)
public abstract class MixinEnvironmentSkyLayers {

    @Inject(method = "addDefaultLayers(Lnet/minecraft/world/attribute/EnvironmentAttributeSystem$Builder;Lnet/minecraft/world/level/Level;)V",
            at = @At("TAIL"))
    private static void golem$skyModifiers(EnvironmentAttributeSystem.Builder builder, Level level, CallbackInfo ci) {
        builder.addTimeBasedLayer(EnvironmentAttributes.SUN_ANGLE, (value, tick) ->
                Skies.active(level) ? Skies.sunAngle(level, value) : value);
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_LEVEL, (value, tick) -> {
            if (!Skies.active(level)) return value;
            float daylight = (value - 4F) / 11F;
            return 4F + 11F * Skies.daylight(level, daylight);
        });
        builder.addPositionalLayer(EnvironmentAttributes.SKY_COLOR, (value, pos, biomes) ->
                Skies.active(level) ? Skies.argb(value, c -> Skies.skyColor(level, c, pos, 0.0F)) : value);
        builder.addPositionalLayer(EnvironmentAttributes.FOG_COLOR, (value, pos, biomes) ->
                Skies.active(level) ? Skies.argb(value, c -> Skies.fogColor(level, c, pos, 0.0F)) : value);
        builder.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (value, tick) ->
                Skies.active(level) ? Skies.cloudColorArgb(level, value) : value);
        builder.addTimeBasedLayer(EnvironmentAttributes.STAR_BRIGHTNESS, (value, tick) ->
                Skies.active(level) ? Skies.starBrightness(level, value) : value);
        // the factor is pre-26.1's sun-brightness * 0.95 + 0.05 and the colour follows the same brightness (Skies)
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_FACTOR, (value, tick) ->
                Skies.active(level) ? Skies.skyLightFactor(level, value) : value);
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, (value, tick) ->
                Skies.active(level) ? Skies.skyLightColor(level, value) : value);
        builder.addTimeBasedLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, (value, tick) ->
                Skies.active(level) ? Skies.sunriseArgb(level, value) : value);
        // dev.golem.core.client.Clouds: vanilla adds its cloud pass only for an opaque cloud colour; a registered
        // level's own layers need the pass while it has any (and none while it has none).
        builder.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (value, tick) ->
                dev.golem.core.client.Clouds.cloudColorAttribute(level, value));
        //? if neoforge {
        builder.addTimeBasedLayer(net.neoforged.neoforge.common.world.NeoForgeEnvironmentAttributes.CUSTOM_CLOUDS,
                (value, tick) -> Clouds.replaces(level)
                        ? dev.golem.core.Ids.of(dev.golem.core.client.Clouds.NAMESPACE, dev.golem.core.client.Clouds.PATH)
                        : value);
        // dev.golem.core.client.Precipitation: Golem's CustomWeatherEffectRenderer for a registered level
        builder.addTimeBasedLayer(net.neoforged.neoforge.common.world.NeoForgeEnvironmentAttributes.CUSTOM_WEATHER_EFFECTS,
                (value, tick) -> dev.golem.core.client.Precipitation.has(level)
                        ? dev.golem.core.Ids.of(dev.golem.core.render.PrecipitationRendering.NAMESPACE,
                                dev.golem.core.render.PrecipitationRendering.PATH)
                        : value);
        //?}
    }
}
//?} else {
/*public final class MixinEnvironmentSkyLayers {
    // Empty below 26.1, where there are no environment attributes; see MixinLevelSkyDarken and MixinClientLevelSky.
    private MixinEnvironmentSkyLayers() {}
}
*///?}
