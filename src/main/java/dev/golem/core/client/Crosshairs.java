package dev.golem.core.client;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

/** Replaces the vanilla crosshair only when a registered renderer claims the current frame. */
public final class Crosshairs {

    private Crosshairs() {}

    @FunctionalInterface
    public interface Renderer {
        boolean render(
                //? if >=26.1 {
                GuiGraphicsExtractor graphics
                //?} else {
                /*GuiGraphics graphics
                *///?}
        );
    }

    private static Renderer renderer = graphics -> false;
    private static boolean registered;

    /** Installs the one conditional crosshair replacement used by the mod. */
    public static void register(Renderer renderer) {
        Crosshairs.renderer = renderer;
        if (registered) return;
        registered = true;

        //? if fabric && >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.replaceElement(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CROSSHAIR,
                vanilla -> (graphics, deltaTracker) -> {
                    if (!render(graphics)) vanilla.extractRenderState(graphics, deltaTracker);
                });
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) -> {
                    if (event.getName().equals(
                            net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR)
                            && render(event.getGuiGraphics())) {
                        event.setCanceled(true);
                    }
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) -> {
                    if (event.getOverlay().equals(
                            net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.type())
                            && render(event.getGuiGraphics())) {
                        event.setCanceled(true);
                    }
                });
        *///?}
        // Legacy Fabric is handled by MixinLegacyCrosshair because HudRenderCallback is post-HUD.
    }

    /** Called by loader events and the legacy Fabric mixin. */
    public static boolean render(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics
            //?} else {
            /*GuiGraphics graphics
            *///?}
    ) {
        return renderer.render(graphics);
    }
}
