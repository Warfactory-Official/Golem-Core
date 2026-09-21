package dev.golem.core.harness;

import dev.golem.core.GolemCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Window policy for a harness client: never take focus, never grab the cursor, never pause because
 * focus is elsewhere, and sit minimized unless {@code -Dgolem.harness.minimize=false}.
 *
 * <p>Rendering continues into the main render target while the window is iconified on every supported
 * version (the framebuffer-resize callback ignores a zero size, and 26.1 skips only the final blit),
 * so screenshots taken from the render target stay valid.
 */
final class HarnessWindow {

    private static boolean applied;
    static boolean autoUnpause = true;
    static boolean minimize = Harness.flag("minimize", true);

    private HarnessWindow() {}

    /** Called from the Window mixin between the default hints and window creation. */
    static void applyCreationHints() {
        if (Harness.port() <= 0) return;
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
    }

    /** True while the harness is active: MouseHandler.grabMouse is turned into a no-op. */
    static boolean blockMouseGrab() {
        return Harness.isActive();
    }

    static long handle(Minecraft minecraft) {
        //? if >=26.1 {
        return minecraft.getWindow().handle();
        //?} else {
        /*return minecraft.getWindow().getWindow();
        *///?}
    }

    static void tick(Minecraft minecraft) {
        if (!applied && minecraft.options != null && minecraft.getWindow() != null) {
            applied = true;
            minecraft.options.pauseOnLostFocus = false;
            //? if >=26.1 {
            // "minimized" would drop the loop to 10 fps for a client that is always minimized.
            minecraft.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.AFK);
            //?}
            if (minimize) setMinimized(minecraft, true);
            GolemCore.LOG.info("[harness] window policy applied (minimize={})", minimize);
        }
        if (autoUnpause && minecraft.screen instanceof PauseScreen && minecraft.level != null) {
            minecraft.setScreen(null);
        }
    }

    static void setMinimized(Minecraft minecraft, boolean minimized) {
        long handle = handle(minecraft);
        if (minimized) GLFW.glfwIconifyWindow(handle);
        else GLFW.glfwRestoreWindow(handle);
    }
}
