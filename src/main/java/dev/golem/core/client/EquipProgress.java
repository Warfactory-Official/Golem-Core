package dev.golem.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * The first-person hand's raise, legacy {@code ItemRenderer.equippedProgress}: 0 fully lowered, 1 fully raised.
 *
 * <p>Vanilla keeps it in {@link ItemInHandRenderer}'s private {@code mainHandHeight}/{@code oMainHandHeight} (the
 * previous tick's value, for interpolation) on every supported version; they are widened by Core's access
 * transformers and wideners, not reached by reflection. A 1.7 item renderer read and wrote these fields to pin the
 * raise (the crucible snapped it to 0 or 1 so its blade never showed half-drawn); {@link #pinMainHand} is that write.
 *
 * <p>One modern difference a pinning renderer has to know about: since 1.9 the hand also dips after every attack
 * while the attack cooldown recovers, with the same item held. 1.7 had no cooldown, so its "the progress fell"
 * only ever meant "the item is being put away". {@link #mainHandSwitching} tells the two apart: the renderer is
 * still showing a different item from the one held.
 *
 * <p>Client-only.
 */
public final class EquipProgress {

    private EquipProgress() {}

    private static ItemInHandRenderer hands() {
        return Minecraft.getInstance().gameRenderer.itemInHandRenderer;
    }

    /** This tick's main-hand raise. */
    public static float mainHand() {
        return hands().mainHandHeight;
    }

    /** The previous tick's main-hand raise (the value the frame interpolates from). */
    public static float previousMainHand() {
        return hands().oMainHandHeight;
    }

    /**
     * Sets both ends of the main hand's interpolation, legacy {@code equippedProgress}/{@code prevEquippedProgress}.
     *
     * <p>Written from an item renderer this lands after the frame has read the raise, and the next tick moves it
     * again, so it cannot hold the hand still against a cooldown: the first frame of every tick still dips. An item
     * that wants 1.7's undipped raise answers {@link dev.golem.core.world.HeldItemState#compatHoldsRaised} instead.
     */
    public static void pinMainHand(float current, float previous) {
        ItemInHandRenderer hands = hands();
        hands.mainHandHeight = current;
        hands.oMainHandHeight = previous;
    }

    /**
     * Whether the hand renderer is still showing a different item from the one the player holds: the lowering is an
     * item change (or its first tick), not the attack-cooldown dip.
     */
    public static boolean mainHandSwitching() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        ItemStack shown = hands().mainHandItem;
        return shown.getItem() != minecraft.player.getMainHandItem().getItem();
    }

    /** Legacy {@code resetEquippedProgress}: drops the hand to fully lowered so it raises again. Public in vanilla. */
    public static void reset(InteractionHand hand) {
        hands().itemUsed(hand);
    }
}
