package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side "a player took a smelting result" with one shape on every loader, the legacy
 * {@code PlayerEvent.ItemSmeltedEvent}. Forge-like loaders answer from their event, Fabric from
 * {@code MixinFurnaceResultSlotTake}; a mod's own furnace output slot calls {@link #taken} itself.
 */
public final class Smelts {

    private Smelts() {}

    public static final Hook<BiConsumer<Player, ItemStack>> TAKE = Hook.notifying();
    private static boolean hooked;

    /** Registers a handler for a smelting result taken on the server: (player, result stack). */
    public static synchronized void onTake(BiConsumer<Player, ItemStack> handler) {
        TAKE.register(handler);
        if (hooked) return;
        hooked = true;
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemSmeltedEvent event) ->
                        taken(event.getEntity(), event.getSmelting()));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerEvent.ItemSmeltedEvent event) ->
                        taken(event.getEntity(), event.getSmelting()));
        *///?}
    }

    /** Dispatches a taken smelting result. Client-side calls are ignored. */
    public static void taken(Player player, ItemStack result) {
        if (TAKE.isEmpty() || player.level().isClientSide() || result.isEmpty()) return;
        for (BiConsumer<Player, ItemStack> handler : TAKE) handler.accept(player, result);
    }
}
