package dev.golem.core.mixin;

import dev.golem.core.player.InventoryScan;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where a player's inventory changes become observable.
 *
 * <p>Neither loader raises an event for a slot edit, and no amount of polling reproduces one: the
 * comparison that decides a slot has changed is vanilla's, against a copy only the menu holds. The
 * one place to be is where the menu is handed to the player, which is also the moment that makes the
 * listener see the initial contents — {@code addSlotListener} broadcasts the whole menu, and every
 * slot differs from an empty last-sent list.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerMenu {

    @Inject(method = "initMenu", at = @At("TAIL"))
    private void golem$watchInventory(AbstractContainerMenu menu, CallbackInfo ci) {
        menu.addSlotListener(new InventoryScan.MenuWatch((ServerPlayer) (Object) this));
    }
}
