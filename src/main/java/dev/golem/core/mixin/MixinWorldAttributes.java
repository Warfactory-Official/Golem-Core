package dev.golem.core.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import dev.golem.core.world.WorldAttributes;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//? if >=26.1 {
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

/**
 * Vanilla and loaders lack ordered attribute/mob hooks; access widening cannot alter runtime rule dispatch.
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
    /*@ModifyReturnValue(method = "isSunBurnTick", at = @At("RETURN"))
    private boolean golem$sunlightBurn(boolean burning) {
        var mob = (Mob) (Object) this;
        return burning && WorldAttributes.MONSTERS_BURN.invoker().apply(mob.level(), mob.blockPosition(), true);
    }
    *///?}
}
