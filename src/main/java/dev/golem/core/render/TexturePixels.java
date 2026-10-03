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
 * itself ({@code texture2DLod(noise, uv, 0)} on a {@code GL_REPEAT} texture is exactly {@link #sampleNearestRepeat}
 * when its magnification filter is {@code GL_NEAREST} and {@link #sampleLinearRepeat} when it is {@code GL_LINEAR}: an
 * explicit level of detail 0 selects the magnification filter).
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

    /**
     * The colour at {@code (u, v)} with bilinear filtering and repeat wrapping (OpenGL's {@code GL_LINEAR} on level 0:
     * texel centres at half-integers), each channel in {@code 0..1}, as {@code {a, r, g, b}}.
     */
    public float[] sampleLinearRepeat(float u, float v) {
        float x = u * width - 0.5F;
        float y = v * height - 0.5F;
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        float[] out = new float[4];
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 2; i++) {
                float w = (i == 0 ? 1 - fx : fx) * (j == 0 ? 1 - fy : fy);
                int c = argb[Math.floorMod(y0 + j, height) * width + Math.floorMod(x0 + i, width)];
                out[0] += w * ((c >>> 24) & 0xFF) / 255F;
                out[1] += w * ((c >> 16) & 0xFF) / 255F;
                out[2] += w * ((c >> 8) & 0xFF) / 255F;
                out[3] += w * (c & 0xFF) / 255F;
            }
        }
        return out;
    }
}
