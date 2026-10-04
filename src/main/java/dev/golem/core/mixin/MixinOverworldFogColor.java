package dev.golem.core.mixin;

//? if <26.1 {
/*import dev.golem.core.world.Skies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * The pre-26.1 fog colour for {@link Skies.Modifier#fogColor}: the overworld's brightness-dependent fog colour is
 * the exact counterpart of 1.7's {@code WorldProvider.getFogColor} (the biome-and-time fog colour, before the
 * renderer blends the sky colour into it by view distance). Forge-like loaders' {@code ComputeFogColor} event
 * fires after that blend, so it would change a different value; this inject is used on every pre-26.1 node so
 * all of them agree. Justification otherwise as {@code MixinClientLevelSky}.
 ^/
@Mixin(DimensionSpecialEffects.OverworldEffects.class)
public abstract class MixinOverworldFogColor {

    @Inject(method = "getBrightnessDependentFogColor", at = @At("RETURN"), cancellable = true)
    private void golem$fogColor(Vec3 color, float brightness, CallbackInfoReturnable<Vec3> cir) {
        var level = Minecraft.getInstance().level;
        if (level != null && Skies.active(level)) cir.setReturnValue(Skies.fogColor(level, cir.getReturnValue()));
    }
}
*///?} else {
public final class MixinOverworldFogColor {
    // Empty on 26.1: the fog colour is the FOG_COLOR environment attribute, see MixinEnvironmentSkyLayers.
    private MixinOverworldFogColor() {}
}
//?}
