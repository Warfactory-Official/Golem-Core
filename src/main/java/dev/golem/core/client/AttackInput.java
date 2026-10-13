package dev.golem.core.client;

import dev.golem.core.hook.Hook;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Takes the attack key away from vanilla while a mod needs the click for itself.
 *
 * <p>While any registered guard answers {@code true}, a click of the attack key does not attack the entity under the
 * crosshair (no attack packet), does not start or continue breaking a block and does not reset the attack cooldown.
 * It only swings the main hand once per click, through {@code LocalPlayer.swing}, so the server, other players and the
 * item's swing hook still see a swing.
 *
 * <p>Before each client tick the buffered clicks are consumed with {@code KeyMapping.consumeClick}, so vanilla's
 * {@code startAttack} never sees them. A held key still reaches {@code continueAttack}, which only the loader can
 * stop: NeoForge and Forge cancel {@code InputEvent.InteractionKeyMappingTriggered}, Fabric uses
 * {@code ClientPreAttackCallback}. No mixin. Client-only.
 */
public final class AttackInput {

    private AttackInput() {}

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final Hook<BooleanSupplier> GUARDS = Hook.notifying();
    private static boolean installed;
    /** Clicks this class consumed so far (harness and tests read it as the positive signal). */
    private static long consumed;

    /** While {@code guard} answers {@code true}, attack clicks only swing the main hand. Installs the bridge itself. */
    public static void consumeWhile(BooleanSupplier guard) {
        GUARDS.register(guard);
        install();
    }

    /** How many attack clicks were taken from vanilla since the game started. */
    public static long consumedClicks() {
        return consumed;
    }

    private static boolean guarded() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return false;
        for (BooleanSupplier guard : GUARDS) {
            if (guard.getAsBoolean()) return true;
        }
        return false;
    }

    /** Before the tick: each buffered click becomes a bare swing. */
    private static void beforeTick() {
        if (!guarded()) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        while (minecraft.options.keyAttack.consumeClick()) {
            consumed++;
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static void install() {
        if (installed) return;
        installed = true;
        ClientTicks.onStartTick(AttackInput::beforeTick);
        //? if fabric {
        /*// clicks are already consumed before the tick; this stops startAttack for any that arrived inside it and the
        // held-key continueAttack (Fabric then stops any block breaking in progress)
        net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback.EVENT.register(
                (client, player, clickCount) -> {
                    if (!guarded()) return false;
                    for (int i = 0; i < clickCount; i++) {
                        consumed++;
                        player.swing(InteractionHand.MAIN_HAND);
                    }
                    return true;
                });
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) -> {
                    if (event.isAttack() && guarded()) {
                        event.setCanceled(true);
                        event.setSwingHand(false);
                    }
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) -> {
                    if (event.isAttack() && guarded()) {
                        event.setCanceled(true);
                        event.setSwingHand(false);
                    }
                });
        *///?}
    }
}
