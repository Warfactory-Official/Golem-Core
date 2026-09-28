package dev.golem.core.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Suppresses individual vanilla HUD elements while a consumer's condition holds.
 *
 * <p>The legacy mods cancelled {@code RenderGameOverlayEvent.Pre} for one {@code ElementType} and drew
 * their own replacement (HEV's Half-Life readout replaces hearts and armour). Every loader since has a
 * named-element equivalent: NeoForge's {@code RenderGuiLayerEvent.Pre}, Forge 1.20.1's
 * {@code RenderGuiOverlayEvent.Pre} and 26.1 Fabric's {@code HudElementRegistry.replaceElement}.
 *
 * <p>Fabric before 26.1 has only a post-HUD callback and no named elements, so there the two rows are cancelled
 * by {@code MixinLegacyHudRows}: {@code Gui.renderHearts} at its head, and the armour row through the one
 * {@code getArmorValue()} read in {@code renderPlayerHealth} (1.20.1 draws it inline) or {@code renderArmor}'s head
 * (1.21.1). The mixin's comment carries the ladder.
 *
 * <p>Client-only: never call this from common initialization.
 */
public final class VanillaHud {

    private VanillaHud() {}

    private static final List<BooleanSupplier> HEALTH = new ArrayList<>();
    private static final List<BooleanSupplier> ARMOR = new ArrayList<>();
    private static boolean registered;

    /** Hides vanilla's hearts and armour icons on every frame for which {@code condition} answers true. */
    public static void hideHealthAndArmor(BooleanSupplier condition) {
        HEALTH.add(condition);
        ARMOR.add(condition);
        install();
    }

    /** Whether the running node can actually suppress the rows (every node, since the legacy Fabric mixin). */
    public static boolean supported() {
        return true;
    }

    /** Whether some condition hides the hearts this frame; read by the loader hooks and the legacy Fabric mixin. */
    public static boolean hideHealth() {
        for (BooleanSupplier condition : HEALTH) if (condition.getAsBoolean()) return true;
        return false;
    }

    /** Whether some condition hides the armour row this frame. */
    public static boolean hideArmor() {
        for (BooleanSupplier condition : ARMOR) if (condition.getAsBoolean()) return true;
        return false;
    }

    private static void install() {
        if (registered) return;
        registered = true;
        //? if fabric && >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.replaceElement(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HEALTH_BAR,
                vanilla -> (graphics, deltaTracker) -> {
                    if (!hideHealth()) vanilla.extractRenderState(graphics, deltaTracker);
                });
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.replaceElement(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.ARMOR_BAR,
                vanilla -> (graphics, deltaTracker) -> {
                    if (!hideArmor()) vanilla.extractRenderState(graphics, deltaTracker);
                });
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) -> {
                    if (event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.PLAYER_HEALTH)
                            && hideHealth()) event.setCanceled(true);
                    if (event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.ARMOR_LEVEL)
                            && hideArmor()) event.setCanceled(true);
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) -> {
                    if (event.getOverlay().equals(
                            net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.PLAYER_HEALTH.type())
                            && hideHealth()) event.setCanceled(true);
                    if (event.getOverlay().equals(
                            net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.ARMOR_LEVEL.type())
                            && hideArmor()) event.setCanceled(true);
                });
        *///?}
    }
}
