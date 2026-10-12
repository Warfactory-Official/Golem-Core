package dev.golem.core.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Takes the attack key away from vanilla while a mod needs the click for itself.
 *
 * <p>While any registered guard answers {@code true}, a click of the attack key neither attacks the entity under the
 * crosshair (no attack packet is sent, so no damage and no knockback), nor starts or continues breaking a block, nor
 * resets the attack cooldown: it only swings the main hand, exactly once per click, which is what vanilla's
 * {@code startAttack} would also have done ({@code LocalPlayer.swing} sends the swing packet, so the server and other
 * players still see a swing and an item's swing hook still runs on both sides).
 *
 * <p>How: before each client tick the buffered clicks are consumed here (vanilla API: {@code KeyMapping.consumeClick},
 * read by {@code handleKeybinds} later in the same tick), so vanilla's {@code startAttack} never sees them. A held key
 * still reaches {@code continueAttack}, which only the loader can stop: NeoForge and Forge cancel it through
 * {@code InputEvent.InteractionKeyMappingTriggered} (no swing, as vanilla does not swing a cancelled hold either),
 * Fabric through {@code ClientPreAttackCallback}. No mixin.
 *
 * <p>Client-only.
 */
public final class AttackInput {

    private AttackInput() {}

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final dev.golem.core.hook.Hook<BooleanSupplier> GUARDS = dev.golem.core.hook.Hook.notifying();
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
