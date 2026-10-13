package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side "an entity finished using an item" (eating, drinking) with one shape on every loader - the legacy
 * {@code PlayerUseItemEvent.Finish} / modern {@code LivingEntityUseItemEvent.Finish}.
 *
 * <p>Handlers receive a copy of the stack as it was <b>before</b> {@code finishUsingItem} consumed it (the Forge-like
 * event's {@code getItem()}), and run after the item's own finish logic, as the legacy event did. Forge-like loaders
 * answer from their event; Fabric, which has none, from {@code MixinLivingEntityUseFinish}.
 */
public final class ItemUses {

    private ItemUses() {}

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final Hook<BiConsumer<LivingEntity, ItemStack>> FINISH = Hook.notifying();
    private static boolean hooked;

    /** Registers a handler for a finished item use on the server: (entity, stack before use). */
    public static synchronized void onFinish(BiConsumer<LivingEntity, ItemStack> handler) {
        FINISH.register(handler);
        if (hooked) return;
        hooked = true;
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) ->
                        finished(event.getEntity(), event.getItem()));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish event) ->
                        finished(event.getEntity(), event.getItem()));
        *///?}
    }

    /** Dispatches a finished use. Called by the loader hooks; client-side calls are ignored. */
    public static void finished(LivingEntity entity, ItemStack before) {
        if (FINISH.isEmpty() || entity.level().isClientSide() || before.isEmpty()) return;
        for (BiConsumer<LivingEntity, ItemStack> handler : FINISH) handler.accept(entity, before);
    }
}
