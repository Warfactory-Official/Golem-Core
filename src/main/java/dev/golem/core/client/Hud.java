package dev.golem.core.client;

import dev.golem.core.Ids;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

/** Client HUD layers share draw order and depth composition while leaving chat above screen effects. */
public final class Hud {

    private Hud() {}
    //? if fabric && <26.1 {
    /*private static final List<Consumer<GuiGraphics>> BEFORE_CHAT = new ArrayList<>();

    public static void drawBeforeChat(GuiGraphics graphics) {
        for (var renderer : BEFORE_CHAT) draw(graphics, renderer);
    }
    *///?}

    //? if >=26.1 {

    /** Draws a screen effect over the HUD while keeping chat and its backdrop readable above it. */
    public static void addBeforeChat(String namespace, String path, Consumer<GuiGraphicsExtractor> renderer) {
        //?} else {
        /*public static void addBeforeChat(String namespace, String path, Consumer<GuiGraphics> renderer) {
         *///?}
        //? if fabric && >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementBefore(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
                Ids.of(namespace, path), (graphics, _) -> renderer.accept(graphics));
        *///?} elif fabric {
        /*BEFORE_CHAT.add(renderer);
         *///?} elif neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) ->
                        event.registerBelow(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CHAT,
                                Ids.of(namespace, path),
                                (graphics, deltaTracker) -> draw(graphics, renderer)));
        //?} elif forge {
        /*dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) ->
                        event.registerBelow(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CHAT_PANEL.id(),
                                namespace + "/" + path, (gui, graphics, partialTick, width, height) -> draw(graphics, renderer)));
        *///?}
    }

    //? if >=26.1 {
    /** Registers an overlay drawn after the vanilla HUD. */
    public static void addLast(String namespace, String path, Consumer<GuiGraphicsExtractor> renderer) {
    //?} else {
    /*/^* Registers an overlay drawn after the vanilla HUD. ^/
    public static void addLast(String namespace, String path, Consumer<GuiGraphics> renderer) {
    *///?}
        //? if fabric && >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                Ids.of(namespace, path), (graphics, deltaTracker) -> renderer.accept(graphics));
        *///?} elif fabric {
        /*// Pre-26.1 fabric-api has one unnamed callback; the id is accepted and ignored.
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                (graphics, deltaTracker) -> draw(graphics, renderer));
        *///?} elif neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) ->
                        event.registerAboveAll(Ids.of(namespace, path),
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
     * every chat line is translated to z 50, so an overlay drawn afterwards at z 0 fails the depth test wherever
     * chat is (power-armour charge bars vanish while chat is visible). Flushing and clearing depth before the
     * layer reproduces "later is on top".
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
