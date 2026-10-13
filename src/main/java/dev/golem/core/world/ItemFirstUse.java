package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An item's right-click on a block, offered to the item <em>before</em> the block gets its own use.
 *
 * <p>Vanilla asks the block first and the item second, so a tool meant to act on a machine (copy its
 * settings, read its network) would otherwise open the machine's menu instead. Forge-era Minecraft had
 * {@code Item.onItemUseFirst} for exactly this; NeoForge and Forge still fire it through
 * {@code PlayerInteractEvent.RightClickBlock}, and Fabric's {@code UseBlockCallback} runs at the same
 * point. This is that one hook under one name. A handler whose result consumes the action ends the interaction
 * with that result: neither the block nor the item's ordinary {@code useOn} runs. Any other result lets the click
 * continue - consumers whose sources spell "pass" differently per version (26.1's {@code TRY_WITH_EMPTY_HAND})
 * need not care which one they return.
 *
 * <p>Handlers run on both logical sides, like the hook they replace. Spectators are never offered the
 * click, matching vanilla's own block-use gate.
 */
public final class ItemFirstUse {

    @FunctionalInterface
    public interface Handler {
        /** A result that does not consume the action ({@code PASS}) lets the click continue as vanilla would. */
        InteractionResult use(ItemStack stack, UseOnContext context);
    }

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final Hook<Handler> HANDLERS = Hook.notifying();
    private static boolean installed;

    private ItemFirstUse() {}

    public static synchronized void register(Handler handler) {
        HANDLERS.register(handler);
        if (installed) return;
        installed = true;
        //? if fabric {
        /*net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                dispatch(player, hand, hit));
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) -> {
                    InteractionResult result = dispatch(event.getEntity(), event.getHand(), event.getHitVec());
                    if (result.consumesAction()) {
                        event.setCanceled(true);
                        event.setCancellationResult(result);
                    }
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) -> {
                    InteractionResult result = dispatch(event.getEntity(), event.getHand(), event.getHitVec());
                    if (result.consumesAction()) {
                        event.setCanceled(true);
                        event.setCancellationResult(result);
                    }
                });
        *///?}
    }

    /**
     * Forge-era {@code Item.doesSneakBypassUse}: a sneaking player holding one of {@code items} still uses the block
     * clicked, as if not sneaking past the gate. Vanilla skips the block then and goes straight to the item. The block
     * sees the sneak ({@code player.isShiftKeyDown()}) as usual; a block that does not take the click passes it on to
     * the item.
     */
    public static void sneakBypass(Predicate<ItemStack> items) {
        register((stack, context) -> {
            Player player = context.getPlayer();
            if (player == null || !player.isSecondaryUseActive() || !items.test(stack)) return InteractionResult.PASS;
            BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                    context.getClickedPos(), context.isInside());
            return useBlock(context.getLevel().getBlockState(context.getClickedPos()), context.getLevel(), player,
                    context.getHand(), stack, hit);
        });
    }

    /** The server's block-use step for a held item, as {@code ServerPlayerGameMode.useItemOn} runs it past the gate. */
    private static InteractionResult useBlock(BlockState state, Level level, Player player, InteractionHand hand,
                                              ItemStack stack, BlockHitResult hit) {
        //? if >=26.1 {
        InteractionResult result = state.useItemOn(stack, level, player, hand, hit);
        if (result instanceof InteractionResult.TryEmptyHandInteraction && hand == InteractionHand.MAIN_HAND) {
            return state.useWithoutItem(level, player, hit);
        }
        return result;
        //?} else if >=1.21.1 {
        /*net.minecraft.world.ItemInteractionResult result = state.useItemOn(stack, level, player, hand, hit);
        if (result == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                && hand == InteractionHand.MAIN_HAND) {
            return state.useWithoutItem(level, player, hit);
        }
        return result.result();
        *///?} else {
        /*return state.use(level, player, hand, hit);
        *///?}
    }

    private static InteractionResult dispatch(Player player, InteractionHand hand, BlockHitResult hit) {
        if (player == null || player.isSpectator() || hit == null) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return InteractionResult.PASS;
        UseOnContext context = new UseOnContext(player, hand, hit);
        for (Handler handler : HANDLERS) {
            InteractionResult result = handler.use(stack, context);
            if (result != null && result.consumesAction()) return result;
        }
        return InteractionResult.PASS;
    }
}
