package dev.golem.core.mixin;

import dev.golem.core.world.CompatItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps an item that rewrites its own state raised in the hand.
 *
 * <p>The hand renderer decides whether the item it is showing is still the one being held, and every
 * version and loader asks that question differently — vanilla's {@code ItemStack.matches} before 26.1,
 * its {@code shouldInstantlyReplaceVisibleItem} on 26.1, Forge's and NeoForge's own re-equip hook in
 * between. Answering it here instead, before any of them run, needs none of that: the stack the
 * renderer is showing is replaced with the one now held, so whatever the version asks next it is
 * comparing a stack with itself.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinHeldItemState {

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Inject(method = "tick", at = @At("HEAD"))
    private void golem$keepStatefulItemRaised(CallbackInfo callback) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        this.mainHandItem = golem$shown(this.mainHandItem, player.getMainHandItem());
        this.offHandItem = golem$shown(this.offHandItem, player.getOffhandItem());
    }

    @Unique
    private static ItemStack golem$shown(ItemStack shown, ItemStack held) {
        return shown != held && shown.getItem() instanceof CompatItem item
                && item.compatIsSameHeldItem(shown, held) ? held : shown;
    }
}
