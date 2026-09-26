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

    /** Receives overlays inside a {@link #layers} batch. */
    @FunctionalInterface
    public interface Sink {
        //? if >=26.1 {
        void addLast(String namespace, String path, Consumer<GuiGraphicsExtractor> renderer);
        //?} else {
        /*void addLast(String namespace, String path, Consumer<GuiGraphics> renderer);
        *///?}
    }

    /** A batch run at the loader's HUD registration moment, after registries flushed; declare during construction. */
    public static void layers(Consumer<Sink> registrations) {
        //? if fabric {
        /*registrations.accept(Hud::addLast);
        *///?} elif neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) -> registrations.accept(
                        (namespace, path, renderer) -> event.registerAboveAll(dev.golem.core.Ids.of(namespace, path),
                                (graphics, deltaTracker) -> renderer.accept(graphics))));
        //?} elif forge {
        /*dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) ->
                        event.enqueueWork(() -> registrations.accept(Hud::addLast)));
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
                dev.golem.core.Ids.of(namespace, path), (graphics, deltaTracker) -> renderer.accept(graphics));
        *///?} elif fabric {
        /*// Pre-26.1 fabric-api has one unnamed callback; the id is accepted and ignored.
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                (graphics, deltaTracker) -> renderer.accept(graphics));
        *///?} elif neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) ->
                        event.registerAboveAll(dev.golem.core.Ids.of(namespace, path),
                                (graphics, deltaTracker) -> renderer.accept(graphics)));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.RenderGuiOverlayEvent.Post event) ->
                        renderer.accept(event.getGuiGraphics()));
        *///?}
    }
}
