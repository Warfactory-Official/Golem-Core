package dev.golem.core.client;

import java.util.function.Consumer;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

/**
 * Draws an overlay above the vanilla HUD.
 *
 * <p>26.1's fabric-api models the HUD as an ordered list of named elements ({@code HudElementRegistry});
 * before that it is a single "after the vanilla HUD" callback with no identity. NeoForge and Forge have
 * their own registries again. All a consumer wants to say is "draw this, on top".
 *
 * <p>The id is taken as namespace and path rather than an {@code Identifier} so this signature does not
 * have to be written twice for that reason as well — the graphics type alone already forces a split,
 * since {@code GuiGraphics} became {@code GuiGraphicsExtractor} when HUD drawing became an extract pass.
 *
 * <p>Client-only: never call this from common initialization.
 */
public final class Hud {

    private Hud() {}

    //? if >=26.1 {
    /** Registers an overlay drawn after the vanilla HUD. */
    public static void addLast(String namespace, String path, Consumer<GuiGraphicsExtractor> renderer) {
    //?} else {
    /*/^* Registers an overlay drawn after the vanilla HUD. ^/
    public static void addLast(String namespace, String path, Consumer<GuiGraphics> renderer) {
    *///?}
        //? if fabric && >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                dev.golem.core.Ids.of(namespace, path), (graphics, deltaTracker) -> renderer.accept(graphics));
        *///?} elif fabric {
        /*// Pre-26.1 fabric-api has one unnamed callback; the id is accepted and ignored.
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                (graphics, deltaTracker) -> draw(graphics, renderer));
        *///?} elif neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) ->
                        event.registerAboveAll(dev.golem.core.Ids.of(namespace, path),
                                (graphics, deltaTracker) -> renderer.accept(graphics)));
        //?} elif forge {
        /*// One named layer above every vanilla overlay. (RenderGuiOverlayEvent.Post, used here before, fires
        // once after EACH vanilla overlay, so a layer drew a dozen times a frame.) Registered from Golem Core's
        // own bus, so the path carries the caller's namespace to keep two mods' ids apart.
        dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) ->
                        event.registerAboveAll(namespace + "/" + path,
                                (gui, graphics, partialTick, width, height) -> draw(graphics, renderer)));
        *///?}
    }

    /**
     * Draws one registered layer with 26.1's composition rule: a later layer is on top of everything drawn
     * before it, whatever depth that was drawn at.
     *
     * <p>26.1 composes the GUI by submission order, and 1.20.5+ separates every HUD layer by
     * {@code LayeredDraw.Z_SEPARATION}. 1.20.1 has neither: the whole HUD shares one depth buffer at raw z, and
     * vanilla draws some of it well above zero — every chat line is translated to z 50 — so an overlay drawn
     * afterwards at z 0 simply failed the depth test wherever the chat (or anything else raised) had been, and
     * power-armour charge bars, which sit exactly where recent chat lines are, did not appear at all while chat
     * was visible. Flushing and clearing depth before the layer reproduces "later is on top" exactly.
     */
    //? if >=26.1 {
    public static void draw(GuiGraphicsExtractor graphics, Consumer<GuiGraphicsExtractor> renderer) {
        renderer.accept(graphics);
    }
    //?} elif >=1.20.5 {
    /*public static void draw(GuiGraphics graphics, Consumer<GuiGraphics> renderer) {
        renderer.accept(graphics);
    }
    *///?} else {
    /*public static void draw(GuiGraphics graphics, Consumer<GuiGraphics> renderer) {
        graphics.flush();
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                net.minecraft.client.Minecraft.ON_OSX);
        renderer.accept(graphics);
    }
    *///?}
}
