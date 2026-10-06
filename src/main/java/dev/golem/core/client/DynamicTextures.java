package dev.golem.core.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Textures made from pixels at run time - legacy {@code TextureManager.getDynamicTextureLocation(name, new
 * DynamicTexture(bufferedImage))}. Pixels are always {@code 0xAARRGGBB}, row-major: {@code NativeImage} keeps ABGR in
 * memory below 26.1 and takes ARGB from 26.1, and its {@code DynamicTexture} constructor gained a debug label at 26.1;
 * both differences stay here. Client-only; call on the render thread.
 */
public final class DynamicTextures {

    private DynamicTextures() {}

    /** Uploads {@code argb} ({@code width * height}) as {@code id}, replacing whatever was registered there. */
    public static void register(
            //? if >=26.1 {
            Identifier id,
            //?} else {
            /*ResourceLocation id,
            *///?}
            int width, int height, int[] argb) {
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = argb[y * width + x];
                //? if >=26.1 {
                image.setPixel(x, y, pixel);
                //?} else {
                /*image.setPixelRGBA(x, y, (pixel & 0xFF00FF00) | ((pixel >> 16) & 0xFF) | ((pixel & 0xFF) << 16));
                *///?}
            }
        }
        //? if >=26.1 {
        DynamicTexture texture = new DynamicTexture(id::toString, image);
        //?} else {
        /*DynamicTexture texture = new DynamicTexture(image);
        *///?}
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }

    /** Decodes a PNG into {@code 0xAARRGGBB} pixels; {@code [0]} and {@code [1]} of {@code size} receive its size. */
    public static int[] decodePng(byte[] png, int[] size) throws IOException {
        try (NativeImage image = NativeImage.read(png)) {
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
            size[0] = w;
            size[1] = h;
            return pixels;
        }
    }
}
