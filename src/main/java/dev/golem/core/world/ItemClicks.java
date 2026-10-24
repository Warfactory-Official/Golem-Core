package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * A right-click with an item in hand (in the air, or on a block that passed the click on), offered before the item's
 * own {@code use}: the Forge-like {@code PlayerInteractEvent.RightClickItem} and Fabric's {@code UseItemCallback}.
 * A handler that consumes the click or fails it ends the click with that result; any other answer passes it on.
 * Handlers run on both logical sides.
 */
public final class ItemClicks {

    @FunctionalInterface
    public interface Handler {
        InteractionResult use(Player player, InteractionHand hand);
    }

    /** Every listener, in phase order; register through {@link #register} or directly, with a phase. */
    public static final Hook<Handler> HANDLERS = Hook.notifying();
    private static boolean installed;

    private ItemClicks() {}

    public static synchronized void register(Handler handler) {
        HANDLERS.register(handler);
        if (installed) return;
        installed = true;
        //? if fabric && >=26.1 {
        /*net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) ->
                dispatch(player, hand));
        *///?} elif fabric {
        /*net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
            InteractionResult result = dispatch(player, hand);
            net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
            if (!ends(result)) return net.minecraft.world.InteractionResultHolder.pass(held);
            if (result == InteractionResult.FAIL) return net.minecraft.world.InteractionResultHolder.fail(held);
            return net.minecraft.world.InteractionResultHolder.success(held);
        });
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) -> {
                    InteractionResult result = dispatch(event.getEntity(), event.getHand());
                    if (ends(result)) {
                        event.setCanceled(true);
                        event.setCancellationResult(result);
                    }
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) -> {
                    InteractionResult result = dispatch(event.getEntity(), event.getHand());
                    if (ends(result)) {
                        event.setCanceled(true);
                        event.setCancellationResult(result);
                    }
                });
        *///?}
    }

    /** Offers a click to every handler; the first that ends it wins, else {@code PASS}. */
    public static InteractionResult dispatch(Player player, InteractionHand hand) {
        if (player == null || player.isSpectator()) return InteractionResult.PASS;
        for (Handler handler : HANDLERS) {
            InteractionResult result = handler.use(player, hand);
            if (ends(result)) return result;
        }
        return InteractionResult.PASS;
    }

    private static boolean ends(InteractionResult result) {
        return result.consumesAction() || result == InteractionResult.FAIL;
    }
}
