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
     * <p>The hand renderer lowers and raises the item whenever the held stack stops matching the one it
     * is showing, components and all. An item that rewrites its own state every tick or every hit — a weapon
     * counting an animation down, a tool spending fuel or charge — therefore never finishes being raised and
     * stays where a freshly drawn item starts. Only a renderer that keeps the pose vanilla hands it can see that,
     * which is why it hides so well.
     *
     * <p>1.7 compared the item and its damage and nothing else, so this is the question it asked.
     * Answering {@code true} keeps the raise the item has already made; a genuine change of item is a
     * different question and still lowers the hand, so an item normally answers by comparing the two
     * items alone (or items and damage, as 1.7 did).
     */
    default boolean compatIsSameHeldItem(ItemStack shown, ItemStack held) {
        return false;
    }

    /**
     * Whether the raised hand ignores the modern cooldowns, as every 1.7 item did: once this item is the one shown,
     * the raise climbs to the top at 0.4 a tick and stays there. Since 1.9 vanilla aims it at the cube of a cooldown
     * instead — the attack-strength scale up to 1.21.x (it dips after every attack and every switch), the item-swap
     * scale on 26.1 (after every switch) — which a 1.7 renderer animating in first person must not see. A lowering
     * for a real item change, and the hands-busy lowering, are left to vanilla.
     */
    default boolean compatHoldsRaised(ItemStack held) {
        return false;
    }
}
