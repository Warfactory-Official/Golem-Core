package dev.golem.core.mixin;

//? if <26.1 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.golem.core.world.Skies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * The pre-26.1 sunrise/sunset glow for {@link Skies.Modifier#sunriseColor} (1.7
 * {@code WorldProvider.calcSunriseSunsetColors}). The effects object is shared per dimension type and has no
 * level, so the client's current level is asked. Mixin justification as {@code MixinClientLevelSky}; one
 * {@code @ModifyReturnValue} that only acts when a modifier is active. The returned array is vanilla's shared buffer,
 * so a changed value is a copy.
 ^/
@Mixin(DimensionSpecialEffects.class)
public abstract class MixinDimensionEffectsSky {

    @ModifyReturnValue(method = "getSunriseColor", at = @At("RETURN"))
    private float[] golem$sunriseColor(float[] rgba) {
        var level = Minecraft.getInstance().level;
        if (level == null || !Skies.active(level)) return rgba;
        return Skies.sunriseColor(level, rgba == null ? null : rgba.clone());
    }
}
*///?} else {
public final class MixinDimensionEffectsSky {
    // Empty on 26.1: the glow is the SUNRISE_SUNSET_COLOR environment attribute, see MixinEnvironmentSkyLayers.
    private MixinDimensionEffectsSky() {}
}
//?}
