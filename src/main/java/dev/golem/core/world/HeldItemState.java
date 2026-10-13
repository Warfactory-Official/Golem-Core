package dev.golem.core.world;

import net.minecraft.world.item.ItemStack;

/**
 * How an item's own state relates to the first-person hand raise. {@link CompatItem} implements it; any other item
 * class (a tool or sword base) may implement it directly. Answered on the client by {@code MixinHeldItemState}, at
 * the hand renderer's tick, so every frame already draws the result.
 */
public interface HeldItemState {

    /**
     * Whether the item already raised in hand is still this one, its components having changed.
     *
     * <p>The hand renderer lowers and raises the item whenever the held stack stops matching the one it is
     * showing, components and all. An item that rewrites its own state every tick or hit (a weapon counting an
     * animation down, a tool spending charge) never finishes being raised and stays where a freshly drawn item
     * starts.
     *
     * <p>1.7 compared the item and its damage only. Answering {@code true} keeps the raise already made; a genuine
     * change of item still lowers the hand, so an item normally compares the two items alone (or items and damage,
     * as 1.7 did).
     */
    default boolean compatIsSameHeldItem(ItemStack shown, ItemStack held) {
        return false;
    }

    /**
     * Whether the raised hand ignores the modern cooldowns, as every 1.7 item did: once this item is the one shown,
     * the raise climbs to the top at 0.4 a tick and stays there. Since 1.9 vanilla aims it at the cube of a cooldown
     * instead (the attack-strength scale up to 1.21.x, which dips after every attack and switch; the item-swap scale
     * on 26.1), which a 1.7 first-person renderer must not see. Lowering for a real item change, and the hands-busy
     * lowering, are left to vanilla.
     */
    default boolean compatHoldsRaised(ItemStack held) {
        return false;
    }
}
