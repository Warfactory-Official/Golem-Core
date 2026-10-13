package dev.golem.core.world;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * An item told about every arm swing of the entity holding it. This is legacy Forge's {@code Item.onEntitySwing}.
 *
 * <p>It runs on <b>both</b> logical sides, from {@code LivingEntity.swing(hand, sendToSwingingEntity)}, before
 * vanilla decides whether to restart the swing. So it fires for every swing call, not only for those that start a
 * new arm animation. On the server, that is every swing packet the client sends, and every tick while a block is being
 * mined. That is what an item that starts its own swing animation from the server needs. Answering {@code true}
 * cancels the vanilla swing (no arm animation, no swing packet to trackers), as in 1.7.
 *
 * <p>Where it is wired: NeoForge's and Forge's own item hook ({@code IItemExtension}/{@code IForgeItem
 * .onEntitySwing}) on {@link CompatItem}, {@link CompatSwordItem} and {@link CompatToolItem}; Fabric has no such
 * hook, so a mixin at the head of {@code LivingEntity.swing} dispatches it there (see
 * {@code MixinLivingEntitySwing}). An item implementing this outside those three bases is still reached on Fabric
 * only; extend one of them.
 */
public interface SwingReceiver {

    /** @return {@code true} to cancel vanilla's swing. */
    boolean compatOnEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand);

    /** Dispatch for the loader hooks: the receiver's answer, or {@code false} for any other item. */
    static boolean dispatch(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return !stack.isEmpty() && stack.getItem() instanceof SwingReceiver receiver
                && receiver.compatOnEntitySwing(stack, entity, hand);
    }

    /**
     * Forge 1.20.1's hook passes no hand: {@code swing(hand)} looked the stack up from it, so the hand holding this
     * very stack is the one swinging.
     */
    static InteractionHand handOf(ItemStack stack, LivingEntity entity) {
        return entity.getOffhandItem() == stack && entity.getMainHandItem() != stack
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
}
