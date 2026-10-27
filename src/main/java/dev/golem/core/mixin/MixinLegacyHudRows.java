package dev.golem.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

// Fabric before 26.1 has only a post-HUD callback. Cancel the dedicated heart and armour methods where available.
// Armour on 1.20.1 and air on both older versions are inline in Gui.renderPlayerHealth: zero at their individual
// scalar reads draws no icons. ModifyExpressionValue composes with other consumers without changing entity state.
//? if fabric && <26.1 {

/*import dev.golem.core.client.VanillaHud;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class MixinLegacyHudRows {

    // Both older versions inline air in renderPlayerHealth. Its only air-supply read controls the
    // number of bubbles: zero produces no full or bursting bubbles, without changing the entity's air.
    @ModifyExpressionValue(method = "renderPlayerHealth", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getAirSupply()I"))
    private int golem$airSupply(int air) {
        return VanillaHud.hideAir() ? 0 : air;
    }

    @Inject(method = "renderHearts", at = @At("HEAD"), cancellable = true)
    private void golem$hideHearts(CallbackInfo callback) {
        if (VanillaHud.hideHealth()) callback.cancel();
    }

    //? if >=1.21 {
    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void golem$hideArmor(CallbackInfo callback) {
        if (VanillaHud.hideArmor()) callback.cancel();
    }
    //?} else {
    /^@com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "renderPlayerHealth", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getArmorValue()I"))
    private int golem$armorValue(int armor) {
        return VanillaHud.hideArmor() ? 0 : armor;
    }
    ^///?}
}
*///?}
