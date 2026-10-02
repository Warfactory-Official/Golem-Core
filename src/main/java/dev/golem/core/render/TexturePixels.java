package dev.golem.core.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;

//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * A texture's pixels on the CPU, for code that sampled a texture in an old vertex shader and now does that arithmetic
 * itself ({@code texture2DLod(noise, uv, 0)} on a {@code GL_NEAREST}, {@code GL_REPEAT} texture is exactly
 * {@link #sampleNearestRepeat}).
 *
 * <p>{@code NativeImage} keeps ABGR in memory below 26.1 and hands out ARGB from 26.1; this class always answers ARGB.
 * Client-only.
 */
public final class TexturePixels {

    private final int width;
    private final int height;
    private final int[] argb;

    private TexturePixels(int width, int height, int[] argb) {
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Reads {@code texture} (a full path such as {@code hbm:textures/misc/noise_2.png}) from the resource manager. */
    public static TexturePixels read(
            //? if >=26.1 {
            Identifier texture
            //?} else {
            /*ResourceLocation texture
            *///?}
    ) throws IOException {
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture).open();
             NativeImage image = NativeImage.read(in)) {
            int w = image.getWidth();
            int h = image.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    //? if >=26.1 {
                    pixels[y * w + x] = image.getPixel(x, y);
                    //?} else {
                    /*int abgr = image.getPixelRGBA(x, y);
                    pixels[y * w + x] = (abgr & 0xFF00FF00) | ((abgr >> 16) & 0xFF) | ((abgr & 0xFF) << 16);
                    *///?}
                }
            }
            return new TexturePixels(w, h, pixels);
        }
    }

    /** The texel at {@code (u, v)} with nearest filtering and repeat wrapping, as {@code 0xAARRGGBB}. */
    public int sampleNearestRepeat(float u, float v) {
        int x = Math.floorMod((int) Math.floor(u * width), width);
        int y = Math.floorMod((int) Math.floor(v * height), height);
        return argb[y * width + x];
    }
}
