package dev.golem.core.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import dev.golem.core.world.WorldAttributes;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//? if >=26.1 {
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

/**
 * 26.1 builds a level's attribute layers in a private static method that no loader event reaches, so the builder's
 * return is the seam. Before 26.1 no loader event answers Mob.isSunBurnTick, whose day check, read before the burn
 * roll, stands where 26.1 reads MONSTERS_BURN. Widening adds neither a layer nor a rule.
 */
//? if >=26.1 {
@Mixin(EnvironmentAttributeSystem.Builder.class)
//?} else {
/*@Mixin(Mob.class)
 *///?}
public abstract class MixinWorldAttributes {
    //? if >=26.1 {
    @Inject(method = "addDefaultLayers", at = @At("RETURN"))
    private void golem$worldLayers(Level level, CallbackInfoReturnable<EnvironmentAttributeSystem.Builder> result) {
        WorldAttributes.apply(level, result.getReturnValue());
    }
    //?} else {
    /*@ModifyExpressionValue(method = "isSunBurnTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isDay()Z"))
    private boolean golem$sunlightBurn(boolean day) {
        var mob = (Mob) (Object) this;
        var level = mob.level();
        return level.isClientSide ? day : WorldAttributes.MONSTERS_BURN.invoker().apply(level, mob.blockPosition(), day);
    }
    *///?}
}
