package com.wf.gemrender.texture;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.ktx.ktxBasisParams;
import org.lwjgl.util.ktx.ktxTexture2;
import org.lwjgl.util.ktx.ktxTextureCreateInfo;

public final class BlockCompressor {
    public static final int BLOCK = 4;

    public static final int GL_COMPRESSED_RGBA_BPTC_UNORM = 0x8E8C;

    public static final int BYTES_PER_BLOCK = 16;

    private static final int VK_FORMAT_R8G8B8A8_UNORM = 37;

    private BlockCompressor() {
    }

    public static int blockBytes(int width, int height) {
        return ceilBlocks(width) * ceilBlocks(height) * BYTES_PER_BLOCK;
    }

    public static Blocks toBc7(int width, int height, byte[] rgba) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IOException("cannot compress a " + width + "x" + height + " image");
        }
        int expected = width * height * KtxImage.BYTES_PER_PIXEL;
        if (rgba.length != expected) {
            throw new IOException("expected " + expected + " bytes for " + width + "x" + height
                    + ", got " + rgba.length);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ktxTextureCreateInfo info = ktxTextureCreateInfo.calloc(stack)
                    .vkFormat(VK_FORMAT_R8G8B8A8_UNORM)
                    .baseWidth(width)
                    .baseHeight(height)
                    .baseDepth(1)
                    .numDimensions(2)
                    .numLevels(1)
                    .numLayers(1)
                    .numFaces(1)
                    .isArray(false)
                    .generateMipmaps(false);

            PointerBuffer handle = stack.mallocPointer(1);

            int created = Libktx.create(info, Libktx.TEXTURE_CREATE_ALLOC_STORAGE, handle);
            if (created != Libktx.SUCCESS) {
                throw new IOException("could not create a KTX2 texture: " + Libktx.errorString(created));
            }

            long address = handle.get(0);
            try {
                return encode(address, width, height, rgba, stack);
            } finally {
                Libktx.destroy(address);
            }
        }
    }

    private static Blocks encode(long address, int width, int height, byte[] rgba, MemoryStack stack)
            throws IOException {
        ByteBuffer pixels = MemoryUtil.memAlloc(rgba.length);
        try {
            pixels.put(rgba)
                    .flip();
            int set = Libktx.setImageFromMemory(address, pixels);
            if (set != Libktx.SUCCESS) {
                throw new IOException("could not load pixels into a KTX2 texture: "
                        + Libktx.errorString(set));
            }
        } finally {
            MemoryUtil.memFree(pixels);
        }

        ktxTexture2 texture = ktxTexture2.create(address);

        ktxBasisParams params = ktxBasisParams.calloc(stack)
                .structSize(ktxBasisParams.SIZEOF)
                .uastc(true)
                .uastcFlags(Libktx.PACK_UASTC_LEVEL_FASTEST)
                .threadCount(Math.max(1, Runtime.getRuntime()
                        .availableProcessors() / 2));

        int compressed = Libktx.compressBasisEx(texture, params);
        if (compressed != Libktx.SUCCESS) {
            throw new IOException("Basis encode failed: " + Libktx.errorString(compressed));
        }

        int transcoded = Libktx.transcodeBasis(texture, Libktx.TTF_BC7_RGBA, 0);
        if (transcoded != Libktx.SUCCESS) {
            throw new IOException("could not transcode to BC7: " + Libktx.errorString(transcoded));
        }

        PointerBuffer pOffset = stack.mallocPointer(1);
        int found = Libktx.imageOffset(address, pOffset);
        if (found != Libktx.SUCCESS) {
            throw new IOException("encoded KTX2 has no level 0: " + Libktx.errorString(found));
        }
        long offset = pOffset.get(0);

        int size = blockBytes(width, height);
        ByteBuffer data = Libktx.data(texture);
        if (data == null || data.capacity() < offset + size) {
            throw new IOException("encoded KTX2 is shorter than the " + size + " bytes BC7 needs for "
                    + width + "x" + height);
        }

        byte[] blocks = new byte[size];
        data.position((int) offset)
                .get(blocks);
        return new Blocks(width, height, GL_COMPRESSED_RGBA_BPTC_UNORM, blocks);
    }

    private static int ceilBlocks(int pixels) {
        return (pixels + BLOCK - 1) / BLOCK;
    }

    public record Blocks(int width, int height, int glFormat, byte[] data) {
        public int uncompressedBytes() {
            return width * height * KtxImage.BYTES_PER_PIXEL;
        }
    }
}
