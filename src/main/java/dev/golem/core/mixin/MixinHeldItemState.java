package dev.golem.core.mixin;

import dev.golem.core.world.HeldItemState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.util.Mth;
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
 * version and loader asks that question differently: vanilla's {@code ItemStack.matches} before 26.1,
 * its {@code shouldInstantlyReplaceVisibleItem} on 26.1, Forge's and NeoForge's own re-equip hook in
 * between. The head answers it first by replacing the stack the renderer is showing with the one now
 * held, so whatever the version asks next compares a stack with itself.
 *
 * <p>The tail answers {@link HeldItemState#compatHoldsRaised}: the raise of a 1.7 item, recomputed after vanilla's
 * update so the frames after this tick already draw it. Ladder (AGENTS.md §7): vanilla has no say in the raise target
 * (1); NeoForge's/Forge's {@code shouldCauseReequipAnimation} and Fabric's {@code allowComponentsUpdateAnimation} only
 * answer the re-equip question, which the head already does for every loader (2); the widened fields (3) are
 * written from the item renderer, which is always one tick late: the frame reads the raise before any item
 * renderer runs and the next tick lowers it again, so the hand dips on the first frame of every tick. This
 * class is the hand renderer's tick hook; the tail adds no target.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinHeldItemState {

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    /** The shown main-hand item was already the held one's item when this tick began (no switch in progress). */
    @Unique
    private boolean golem$sameItemAtHead;

    @Inject(method = "tick", at = @At("HEAD"))
    private void golem$keepStatefulItemRaised(CallbackInfo callback) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        this.mainHandItem = golem$shown(this.mainHandItem, player.getMainHandItem());
        this.offHandItem = golem$shown(this.offHandItem, player.getOffhandItem());
        this.golem$sameItemAtHead = this.mainHandItem.getItem() == player.getMainHandItem().getItem();
    }

    /**
     * 1.7 {@code ItemRenderer.updateEquippedItem} for an item that asks for it: with the item already shown, the raise
     * climbs by at most 0.4 a tick to 1 and no cooldown lowers it. Main hand only; 1.7 had one.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void golem$holdLegacyRaise(CallbackInfo callback) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !this.golem$sameItemAtHead || player.isHandsBusy()) return;
        ItemStack held = player.getMainHandItem();
        if (this.mainHandItem == held && held.getItem() instanceof HeldItemState state && state.compatHoldsRaised(held)) {
            this.mainHandHeight = this.oMainHandHeight + Mth.clamp(1.0F - this.oMainHandHeight, -0.4F, 0.4F);
        }
    }

    @Unique
    private static ItemStack golem$shown(ItemStack shown, ItemStack held) {
        return shown != held && shown.getItem() instanceof HeldItemState item
                && item.compatIsSameHeldItem(shown, held) ? held : shown;
    }
}
