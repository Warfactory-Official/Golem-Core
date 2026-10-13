package dev.golem.core.mixin;

//? if <1.20.5 {
/*import dev.golem.core.world.Physics;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * 1.20.1: fall damage reads the backported safe fall distance and fall damage multiplier ({@link Physics}), as 1.20.5's
 * {@code calculateFallDamage} does. With both at their defaults (3 and 1) the result is 1.20.1's own, to the float:
 * {@code ceil((distance - 3 - jumpBoost) * multiplier)}.
 *
 * <p>Why a mixin (AGENTS.md section 7). Rung 1: the formula is a protected vanilla method with the 3 written in. Rung 2: Forge's
 * {@code LivingFallEvent} can scale the distance but not express a safe distance and a multiplier that compose with other
 * mods, and Fabric has no fall event. Rung 3: not a visibility problem. The smallest mixin computes the 1.20.5 formula at
 * the head of {@code calculateFallDamage}.
 ^/
@Mixin(LivingEntity.class)
public abstract class MixinLegacyLivingPhysics {

    @Inject(method = "calculateFallDamage", at = @At("HEAD"), cancellable = true)
    private void golem$fallDamage(float distance, float multiplier, CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) return;
        MobEffectInstance jump = self.getEffect(MobEffects.JUMP);
        float boost = jump == null ? 0.0F : (float) (jump.getAmplifier() + 1);
        float safe = (float) Physics.safeFallDistance(self).getValue();
        float scale = (float) Physics.fallDamageMultiplier(self).getValue();
        cir.setReturnValue(Mth.ceil((distance - safe - boost) * multiplier * scale));
    }
}
*///?}
