package dev.golem.core.mixin;

//? if <26.1 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.golem.core.world.Skies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * The pre-26.1 fog colour for {@link Skies.Modifier#fogColor}: the overworld's brightness-dependent fog colour is
 * the exact counterpart of 1.7's {@code WorldProvider.getFogColor} (the biome-and-time fog colour, before the
 * renderer blends the sky colour into it by view distance). Forge-like loaders' {@code ComputeFogColor} event
 * fires after that blend, so it would change a different value; this hook is used on every pre-26.1 node so
 * all of them agree. Justification otherwise as {@code MixinClientLevelSky} (a chaining {@code @ModifyReturnValue}).
 ^/
@Mixin(DimensionSpecialEffects.OverworldEffects.class)
public abstract class MixinOverworldFogColor {

    @ModifyReturnValue(method = "getBrightnessDependentFogColor", at = @At("RETURN"))
    private Vec3 golem$fogColor(Vec3 color) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        return level != null && Skies.active(level)
                ? Skies.fogColor(level, color, mc.gameRenderer.getMainCamera().getPosition(), 0.0F) : color;
    }
}
*///?} else {
public final class MixinOverworldFogColor {
    // Empty on 26.1: the fog colour is the FOG_COLOR environment attribute, see MixinEnvironmentSkyLayers.
    private MixinOverworldFogColor() {}
}
//?}
