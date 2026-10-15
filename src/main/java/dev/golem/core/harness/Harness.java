package dev.golem.core.harness;

import dev.golem.core.GolemCore;
import dev.golem.core.client.ClientTicks;
import dev.golem.core.client.Hud;
import dev.golem.core.platform.Platform;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Development-only in-game listener for the visual test harness (CLI: {@code Golem-Core/harness}, see its README).
 *
 * <p>Active only when {@code -Dgolem.harness.port=<n>} (or {@code GOLEM_HARNESS_PORT}) is set on a
 * physical client in a development environment. It then accepts JSON-lines commands on a loopback
 * socket and executes them on the client thread: synthetic keyboard and mouse input through the vanilla
 * handlers, structured state queries, screenshots, world open/close, and window policy (never focused,
 * never grabbing the mouse, minimized by default, never pausing on lost focus).
 *
 * <p>Everything here is a facade over per-version vanilla API; scenarios live outside the mod.
 */
public final class Harness {

    private static boolean initialised;
    private static long tick;

    private Harness() {}

    /** The listener port, or 0 when the harness is not requested. */
    public static int port() {
        String value = System.getProperty("golem.harness.port");
        if (value == null || value.isBlank()) value = System.getenv("GOLEM_HARNESS_PORT");
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            GolemCore.LOG.warn("[harness] ignoring malformed port {}", value);
            return 0;
        }
    }

    /** True when {@code -Dgolem.harness.<key>} or {@code GOLEM_HARNESS_<KEY>} is unset or not "false". */
    public static boolean flag(String key, boolean fallback) {
        String value = System.getProperty("golem.harness." + key);
        if (value == null || value.isBlank()) {
            value = System.getenv("GOLEM_HARNESS_" + key.toUpperCase(Locale.ROOT));
        }
        if (value == null || value.isBlank()) return fallback;
        return !value.equalsIgnoreCase("false") && !value.equals("0");
    }

    public static boolean isActive() {
        return initialised;
    }

    /** Current client tick counter, incremented at the end of every client tick. */
    public static long tick() {
        return tick;
    }

    /**
     * Whether a harness-driven key is held. The harness presses keys through vanilla's handlers, which a
     * physical poll ({@code glfwGetKey}) never sees; {@code KeyMappings}' physical polls ask here as well so
     * code that polls a key reacts to a harness press as it would to a real one. Answers false without touching
     * {@code HarnessInput} (and its GLFW name table) on a client where the harness never started.
     */
    public static boolean keyHeld(int glfwKey) {
        return initialised && HarnessInput.keyHeld(glfwKey);
    }

    /** {@link #keyHeld} for a mouse button ({@code glfwGetMouseButton} never sees a harness press either). */
    public static boolean mouseHeld(int button) {
        return initialised && HarnessInput.mouseHeld(button);
    }

    /** Called once from each loader's client initialisation. Safe to call twice. */
    public static void init() {
        if (initialised) return;
        int port = port();
        if (port <= 0) return;
        if (!Platform.isClient()) return;
        if (!Platform.isDevelopment()) {
            GolemCore.LOG.warn("[harness] refusing to start outside a development environment");
            return;
        }
        initialised = true;
        GolemCore.LOG.info("[harness] starting listener on 127.0.0.1:{}", port);
        HarnessLog.install();
        HarnessCommands.registerDefaults();
        ClientTicks.onEndTick(Harness::onTick);
        Hud.addLast(GolemCore.MOD_ID, "harness", graphics -> HarnessScreenshot.render());
        HarnessServer.start(port);
    }

    private static void onTick() {
        tick++;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            HarnessWindow.tick(minecraft);
            HarnessInput.tick(minecraft);
            HarnessScreenshot.tick(minecraft);
            HarnessCamera.tick(minecraft);
            HarnessSounds.tick(minecraft);
            HarnessServer.drain(minecraft);
        } catch (Throwable t) {
            GolemCore.LOG.error("[harness] tick failed", t);
        }
    }
}
