package dev.golem.core.mixin;

//? if <26.1 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.golem.core.world.Skies;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * The pre-26.1 client half of {@link Skies.Modifier}: sky colour, cloud colour, star brightness and the sky's light
 * map factor are read from these four {@code ClientLevel} getters, 1.7's {@code WorldProvider.getSkyColor},
 * {@code drawClouds}, {@code getStarBrightness} and {@code getSunBrightness}.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla takes these from the dimension's fixed effects and the biome;
 * (2) no loader has events for them (Forge-likes have one for the fog colour only, see
 * {@code MixinOverworldFogColor}); (3) widening cannot change a computed return value. Four MixinExtras
 * {@code @ModifyReturnValue}s that only act when a modifier is active; unlike a cancellable RETURN inject they chain
 * with sky and shader mods' own hooks on these getters instead of returning past them.
 ^/
@Mixin(ClientLevel.class)
public abstract class MixinClientLevelSky {

    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3 golem$skyColor(Vec3 color) {
        ClientLevel self = (ClientLevel) (Object) this;
        return Skies.active(self) ? Skies.skyColor(self, color) : color;
    }

    @ModifyReturnValue(method = "getCloudColor", at = @At("RETURN"))
    private Vec3 golem$cloudColor(Vec3 color) {
        ClientLevel self = (ClientLevel) (Object) this;
        return Skies.active(self) ? Skies.cloudColor(self, color) : color;
    }

    @ModifyReturnValue(method = "getStarBrightness", at = @At("RETURN"))
    private float golem$starBrightness(float brightness) {
        ClientLevel self = (ClientLevel) (Object) this;
        return Skies.active(self) ? Skies.starBrightness(self, brightness) : brightness;
    }

    @ModifyReturnValue(method = "getSkyDarken", at = @At("RETURN"))
    private float golem$skyLight(float light) {
        ClientLevel self = (ClientLevel) (Object) this;
        return Skies.active(self) ? Skies.skyLight(self, light) : light;
    }
}
*///?} else {
public final class MixinClientLevelSky {
    // Empty on 26.1: the sky values are environment attributes there, see MixinEnvironmentSkyLayers.
    private MixinClientLevelSky() {}
}
//?}
