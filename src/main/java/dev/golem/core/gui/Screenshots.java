package dev.golem.core.gui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * Reading back what the client last drew, on every version.
 *
 * <p>1.20.1 and 1.21.1 read the main render target synchronously; 26.1's renderer queues the copy and hands the
 * image over when the GPU is done. Callers get the image through a callback either way and own it: close it (or
 * use try-with-resources) when finished. Call from the client thread between frames, e.g. a screen's
 * {@code tick()}: the render target then still holds the last fully composed frame, open screens included.
 */
public final class Screenshots {

    private Screenshots() {}

    /** Captures the whole main render target; the image's origin is its top-left corner. */
    public static void capture(Consumer<NativeImage> consumer) {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=26.1 {
        net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), consumer::accept);
        //?} else {
        /*consumer.accept(net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget()));
        *///?}
    }

    /** A copy of the rectangle {@code x, y, width, height} of {@code source}. */
    public static NativeImage crop(NativeImage source, int x, int y, int width, int height) {
        NativeImage target = new NativeImage(width, height, false);
        source.copyRect(target, x, y, 0, 0, width, height, false, false);
        return target;
    }

    /**
     * Makes every pixel of exactly {@code argb} fully transparent, the chroma key 1.7's wiki renderer used on its
     * magenta slot background.
     */
    public static void keyOut(NativeImage image, int argb) {
        // NativeImage stores ABGR below 26.1, so red and blue are swapped there before comparing.
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                //? if >=26.1 {
                if (image.getPixel(x, y) == argb) image.setPixel(x, y, 0);
                //?} else {
                /*int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                if (image.getPixelRGBA(x, y) == abgr) image.setPixelRGBA(x, y, 0);
                *///?}
            }
        }
    }
}
