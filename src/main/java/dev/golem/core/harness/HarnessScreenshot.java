package dev.golem.core.harness;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import dev.golem.core.GolemCore;
import dev.golem.core.gui.Screenshots;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Captures the main render target after the world, HUD and open screen have been rendered.
 * {@code skip_frames} counts completed frames independently of the client tick rate.
 */
final class HarnessScreenshot {

    private static Path pending;
    private static volatile JsonObject result;
    private static int framesToSkip;
    private static long completedFrames;

    private HarnessScreenshot() {
    }

    /**
     * Completes after vanilla has rendered the changed view and refreshed its crosshair pick.
     */
    static HarnessServer.Pending afterFrame(JsonObject answer) {
        long previous = completedFrames;
        return minecraft -> completedFrames > previous ? answer : null;
    }

    /**
     * Queues a capture; the returned pending completes when the PNG is written.
     */
    static HarnessServer.Pending request(Path path, int skipFrames) {
        pending = path;
        result = null;
        framesToSkip = Math.max(0, skipFrames);
        return minecraft -> result;
    }

    /**
     * Called after GameRenderer.render, including its screen pass.
     */
    static void completedFrame() {
        completedFrames++;
        if (pending == null) return;
        if (framesToSkip-- > 0) return;
        capture();
    }

    private static void capture() {
        Path path = pending;
        pending = null;
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
        } catch (IOException e) {
            result = Json.error("cannot create " + path.getParent() + ": " + e);
            return;
        }
        try {
            Screenshots.capture(image -> write(image, path));
        } catch (RuntimeException t) {
            GolemCore.LOG.error("[harness] screenshot failed", t);
            result = Json.error("screenshot failed: " + t);
        }
    }

    private static void write(NativeImage image, Path path) {
        try (image) {
            image.writeToFile(path);
            JsonObject json = Json.ok();
            json.addProperty("path", path.toAbsolutePath().toString());
            json.addProperty("width", image.getWidth());
            json.addProperty("height", image.getHeight());
            result = json;
        } catch (IOException e) {
            result = Json.error("cannot write " + path + ": " + e);
        }
    }
}
