package dev.golem.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.golem.core.world.Physics;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Vanilla and loaders expose no gravity or vertical-drag hook at these travel phases.
 * Widening cannot change the arithmetic; compose it without replacing native travel.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingTravel {

    //? if >=26.1 {
    @ModifyExpressionValue(method = "getEffectiveGravity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getGravity()D"))
    private double golemcore$effectiveGravity(double gravity) {
        return Physics.effectiveGravity((LivingEntity) (Object) this, gravity);
    }

    // The air branch retains the horizontal friction selected from the ground block.
    @ModifyConstant(method = "travelInAir", constant = @Constant(floatValue = 0.98F))
    private float golemcore$verticalAirDrag(float vertical, @Local(ordinal = 1) float horizontal) {
        return (float) Physics.verticalAirDrag((LivingEntity) (Object) this, vertical, horizontal);
    }
    //?} else {
    /*//? if >=1.20.5 {
    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getGravity()D"))
    private double golemcore$effectiveGravity(double gravity) {
        return Physics.effectiveGravity((LivingEntity) (Object) this, gravity);
    }
    //?} elif forge {
    /^* Forge 1.20.1 reads its gravity attribute after installing its Slow Falling modifier. ^/
    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;getValue()D", ordinal = 0))
    private double golemcore$effectiveGravity(double gravity) {
        return Physics.effectiveGravity((LivingEntity) (Object) this, gravity);
    }
    //?}

    // travel also contains Elytra's 0.98 drag; only the air arm after relative friction belongs to this hook.
    @ModifyConstant(method = "travel", constant = @Constant(doubleValue = 0.9800000190734863D),
            slice = @Slice(from = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/entity/LivingEntity;handleRelativeFrictionAndCalculateMovement(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;")))
    private double golemcore$verticalAirDrag(double vertical,
            @Local(ordinal = 1) float horizontal
    ) {
        return Physics.verticalAirDrag((LivingEntity) (Object) this, vertical, horizontal);
    }
    *///?}
}
