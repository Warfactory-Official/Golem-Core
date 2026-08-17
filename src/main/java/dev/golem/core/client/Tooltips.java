package dev.golem.core.client;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Appends lines to an item's tooltip.
 *
 * <p>The callback's own arity changed with data components: from 1.20.5 fabric-api passes a tooltip
 * <em>context</em> and a display type alongside the stack, before that only the stack and a flag.
 * None of the extra arguments are needed to append a line, so the neutral hook takes the two things
 * every era agrees on.
 *
 * <p>Client-only: never call this from common initialization.
 */
public final class Tooltips {

    private Tooltips() {}

    /** A contribution to one item's tooltip. */
    @FunctionalInterface
    public interface Source {
        void addLines(ItemStack stack, List<Component> lines);
    }

    /** Registers a tooltip contributor for every item. */
    public static void onItemTooltip(Source source) {
        //? if fabric && >=1.20.5 {
        /*net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
                (stack, tooltipContext, tooltipType, lines) -> source.addLines(stack, lines));
        *///?} elif fabric {
        /*net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
                (stack, tooltipContext, lines) -> source.addLines(stack, lines));
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) ->
                        source.addLines(event.getItemStack(), event.getToolTip()));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.ItemTooltipEvent event) ->
                        source.addLines(event.getItemStack(), event.getToolTip()));
        *///?}
    }
}
