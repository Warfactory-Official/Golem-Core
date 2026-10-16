package dev.golem.core.client;

import dev.golem.core.harness.HarnessHooks;

/** Physical mouse state and a callback that runs before vanilla consumes mapped clicks. */
public final class MouseInput {

    @FunctionalInterface
    public interface ButtonListener {
        void onButton(int button, boolean pressed);
    }

    private static ButtonListener buttonListener = (button, pressed) -> {};
    private static boolean registered;

    private MouseInput() {}

    /** Registers the one raw mouse-button observer used by the mod. */
    public static void onButton(ButtonListener listener) {
        buttonListener = listener;
        if (registered) return;
        registered = true;

        // Fabric has no world-input event. MixinMouseInput invokes fireButton from the tail of the
        // raw mouse callback, after mappings update but before Minecraft handles buffered clicks.
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.InputEvent.MouseButton.Post event) ->
                        fireButton(event.getButton(), event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.InputEvent.MouseButton.Post event) ->
                        fireButton(event.getButton(), event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS));
        *///?}
    }

    /** Whether a physical mouse button is held, independent of any drained vanilla mapping. */
    public static boolean isDown(int button) {
        if (button < 0) return false;
        if (HarnessHooks.mouseHeld(button)) return true;
        //? if >=26.1 {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
        //?} else {
        /*long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        *///?}
        return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, button) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    /** Called by the legacy Fabric mixin and Forge-like post-input events. */
    public static void fireButton(int button, boolean pressed) {
        buttonListener.onButton(button, pressed);
    }
}
