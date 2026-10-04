package dev.golem.core.mixin;

//? if <26.1 {
/*import dev.golem.core.world.Skies;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * The pre-26.1 client half of {@link Skies.Modifier}: sky colour, cloud colour, star brightness and the sky's light
 * map factor are read from these four {@code ClientLevel} getters, 1.7's {@code WorldProvider.getSkyColor},
 * {@code drawClouds}, {@code getStarBrightness} and {@code getSunBrightness}.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla takes these from the dimension's fixed effects and the biome;
 * (2) no loader has events for them (Forge-likes have one for the fog colour only, see
 * {@code MixinOverworldFogColor}); (3) widening cannot change a computed return value. Four RETURN injects that
 * only act when a modifier is active.
 ^/
@Mixin(ClientLevel.class)
public abstract class MixinClientLevelSky {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void golem$skyColor(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        ClientLevel self = (ClientLevel) (Object) this;
        if (Skies.active(self)) cir.setReturnValue(Skies.skyColor(self, cir.getReturnValue()));
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void golem$cloudColor(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        ClientLevel self = (ClientLevel) (Object) this;
        if (Skies.active(self)) cir.setReturnValue(Skies.cloudColor(self, cir.getReturnValue()));
    }

    @Inject(method = "getStarBrightness", at = @At("RETURN"), cancellable = true)
    private void golem$starBrightness(float partialTick, CallbackInfoReturnable<Float> cir) {
        ClientLevel self = (ClientLevel) (Object) this;
        if (Skies.active(self)) cir.setReturnValue(Skies.starBrightness(self, cir.getReturnValue()));
    }

    @Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
    private void golem$skyLight(float partialTick, CallbackInfoReturnable<Float> cir) {
        ClientLevel self = (ClientLevel) (Object) this;
        if (Skies.active(self)) cir.setReturnValue(Skies.skyLight(self, cir.getReturnValue()));
    }
}
*///?} else {
public final class MixinClientLevelSky {
    // Empty on 26.1: the sky values are environment attributes there, see MixinEnvironmentSkyLayers.
    private MixinClientLevelSky() {}
}
//?}
