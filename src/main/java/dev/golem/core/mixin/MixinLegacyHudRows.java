package dev.golem.core.mixin;

// VanillaHud on Fabric before 26.1. Ladder (AGENTS.md §7): vanilla has no per-row HUD hook (1); NeoForge and Forge
// cancel their named layers and 26.1 Fabric replaces its named HudElementRegistry elements (2), but Fabric API
// before 26.1 offers only a post-HUD callback that cannot stop a vanilla row; an access widener (3) cannot add a
// condition. So: cancel Gui.renderHearts at its head, and for the armour row cancel 1.21.1's renderArmor at its head
// or, on 1.20.1 where the row is drawn inline in renderPlayerHealth, answer 0 to its single getArmorValue() read
// (the row draws nothing for 0 armour) - a MixinExtras @ModifyExpressionValue on that read only because 1.20.1 has no
// method boundary to inject at (it chains with other mods' hooks on the read, which a @Redirect would not).
//? if fabric && <26.1 {

/*import dev.golem.core.client.VanillaHud;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class MixinLegacyHudRows {

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
