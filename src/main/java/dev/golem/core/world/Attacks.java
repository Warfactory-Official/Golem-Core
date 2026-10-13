package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The server-side "player swings at an entity" hook, legacy {@code AttackEntityEvent}, with one
 * shape on every loader. {@link #onPlayerAttack} handlers observe the attack; {@link #interceptPlayerAttack}
 * handlers may take it over, which is 1.7's {@code Item.onLeftClickEntity} returning {@code true}.
 */
public final class Attacks {

    private Attacks() {}

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final Hook<BiConsumer<Player, Entity>> HANDLERS = Hook.notifying();
    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final Hook<BiPredicate<Player, Entity>> INTERCEPTORS = Hook.notifying();
    private static boolean installed;

    public static void onPlayerAttack(BiConsumer<Player, Entity> handler) {
        HANDLERS.register(handler);
    }

    /**
     * Lets {@code handler} replace a player's melee attack: returning {@code true} cancels vanilla's
     * damage, knockback and sweep for that swing. Runs on both logical sides, before the attack, so the
     * client stops predicting a hit the server will not make; do side effects only when
     * {@code !player.level().isClientSide()}. The attack packet is still sent. Installs the bridge itself.
     */
    public static void interceptPlayerAttack(BiPredicate<Player, Entity> handler) {
        INTERCEPTORS.register(handler);
        install();
    }

    /** Whether an interceptor took the attack; observers only hear attacks nobody took. */
    private static boolean dispatch(Player player, Entity target) {
        for (BiPredicate<Player, Entity> interceptor : INTERCEPTORS) {
            if (interceptor.test(player, target)) return true;
        }
        if (player.level().isClientSide()) return false;
        for (BiConsumer<Player, Entity> handler : HANDLERS) handler.accept(player, target);
        return false;
    }

    /** Installs the loader bridge once. Call after the loader has constructed its event bus. */
    public static void install() {
        if (installed) return;
        installed = true;

        //? if fabric {
        /*net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(
                (player, level, hand, entity, hit) -> dispatch(player, entity)
                        ? net.minecraft.world.InteractionResult.SUCCESS
                        : net.minecraft.world.InteractionResult.PASS);
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) -> {
                    if (dispatch(event.getEntity(), event.getTarget())) event.setCanceled(true);
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.AttackEntityEvent event) -> {
                    if (dispatch(event.getEntity(), event.getTarget())) event.setCanceled(true);
                });
        *///?}
    }
}
