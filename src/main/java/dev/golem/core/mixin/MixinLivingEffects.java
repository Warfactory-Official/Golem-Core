package dev.golem.core.mixin;

import dev.golem.core.world.Effects;
import java.util.Collection;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla has no post-removal listener; loader removal events do not cover this committed client/server path.
 * Widening cannot publish removal, so notify after the native effect callback.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEffects {

    //? if >=26.1 {
    @Inject(method = "onEffectsRemoved", at = @At("TAIL"))
    private void golem$effectsRemoved(Collection<MobEffectInstance> effects, CallbackInfo ci) {
        for (var effect : effects) Effects.removed((LivingEntity) (Object) this, effect);
    }
    //?} else {
    /*@Inject(method = "onEffectRemoved", at = @At("TAIL"))
    private void golem$effectRemoved(MobEffectInstance effect, CallbackInfo ci) {
        Effects.removed((LivingEntity) (Object) this, effect);
    }
    *///?}
}
