package dev.golem.core.harness;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import dev.golem.core.GolemCore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/**
 * Captures the main render target to a PNG from the client tick. Between frames the render target
 * still holds the last fully composed frame, world, HUD and any open screen included, whereas the
 * HUD render pass runs before screens are drawn and would miss them. {@code skip_frames} counts ticks
 * to wait first, so a change made by the previous command has been rendered at least once.
 */
final class HarnessScreenshot {

    private static Path pending;
    private static volatile JsonObject result;
    private static int framesToSkip;

    private HarnessScreenshot() {}

    /** Queues a capture; the returned pending completes when the PNG is written. */
    static HarnessServer.Pending request(Path path, int skipFrames) {
        pending = path;
        result = null;
        framesToSkip = Math.max(0, skipFrames);
        return minecraft -> result;
    }

    /** HUD render callback: kept as a hook; captures happen on the tick. */
    static void render() {}

    /** End-of-tick capture of the last composed frame. */
    static void tick(Minecraft minecraft) {
        if (pending == null) return;
        if (framesToSkip-- > 0) return;
        capture(minecraft);
    }

    private static void capture(Minecraft minecraft) {
        Path path = pending;
        pending = null;
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
        } catch (IOException e) {
            result = Json.error("cannot create " + path.getParent() + ": " + e);
            return;
        }
        try {
            //? if >=26.1 {
            net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> write(image, path));
            //?} else {
            /*NativeImage image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
            write(image, path);
            *///?}
        } catch (Throwable t) {
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
