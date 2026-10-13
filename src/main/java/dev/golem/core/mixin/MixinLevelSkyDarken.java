package dev.golem.core.mixin;

//? if <26.1 {
/*import dev.golem.core.world.Skies;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * The pre-26.1 half of {@link Skies#daylight}: re-derives the level's sky darkening from the modified daylight
 * factor after vanilla computed it, on the server (mob spawning, plant light) and the client alike. Mirrors 1.7's
 * {@code WorldProvider.getSunBrightnessFactor} feeding {@code calculateSkylightSubtracted}.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla computes {@code skyDarken} from time and weather only;
 * (2) no loader has a sky-darkness event; (3) widening the field would still need a write at the moment vanilla
 * recomputes it. The inject is one TAIL call that only acts when a modifier is active, and then repeats vanilla's
 * own formula with the daylight passed through the modifiers.
 ^/
@Mixin(Level.class)
public abstract class MixinLevelSkyDarken {

    @Shadow
    private int skyDarken;

    @Inject(method = "updateSkyBrightness", at = @At("TAIL"))
    private void golem$modifySkyDarken(CallbackInfo ci) {
        Level self = (Level) (Object) this;
        if (!Skies.active(self)) return;
        double d0 = 1.0 - (double) (self.getRainLevel(1.0F) * 5.0F) / 16.0;
        double d1 = 1.0 - (double) (self.getThunderLevel(1.0F) * 5.0F) / 16.0;
        double d2 = 0.5 + 2.0 * Mth.clamp((double) Mth.cos(self.getTimeOfDay(1.0F) * (float) (Math.PI * 2)), -0.25, 0.25);
        float daylight = Skies.daylight(self, (float) (d2 * d0 * d1));
        this.skyDarken = (int) ((1.0 - daylight) * 11.0);
    }
}
*///?} else {
public final class MixinLevelSkyDarken {
    // Empty on 26.1, where the sky darkening is an environment attribute; see MixinEnvironmentSkyLayers.
    private MixinLevelSkyDarken() {}
}
//?}
