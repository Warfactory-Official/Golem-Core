package com.wf.gemrender.texture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.ktx.ktxTexture2;

public record KtxImage(int width, int height, byte[] rgba) {
    public static final int BYTES_PER_PIXEL = 4;
    private static final int VK_FORMAT_R8G8B8A8_UNORM = 37;
    private static final int VK_FORMAT_R8G8B8A8_SRGB = 43;

    public static KtxImage read(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();

        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        try {
            buffer.put(bytes)
                    .flip();
            return decode(buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public static KtxImage decode(ByteBuffer ktx2) throws IOException {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer handle = stack.mallocPointer(1);

            int created = Libktx.createFromMemory(ktx2, Libktx.TEXTURE_CREATE_LOAD_IMAGE_DATA_BIT, handle);
            if (created != Libktx.SUCCESS) {
                throw new IOException("not a readable KTX2 file: " + Libktx.errorString(created));
            }

            long address = handle.get(0);
            ktxTexture2 texture = ktxTexture2.create(address);
            try {
                return toRgba(texture, address);
            } finally {
                Libktx.destroy(address);
            }
        }
    }

    private static KtxImage toRgba(ktxTexture2 texture, long address) throws IOException {
        if (Libktx.needsTranscoding(texture)) {
            int transcoded = Libktx.transcodeBasis(texture, Libktx.TTF_RGBA32, 0);
            if (transcoded != Libktx.SUCCESS) {
                throw new IOException("could not transcode Basis data to RGBA8: "
                        + Libktx.errorString(transcoded));
            }
        }

        int format = texture.vkFormat();
        if (format != VK_FORMAT_R8G8B8A8_UNORM && format != VK_FORMAT_R8G8B8A8_SRGB) {
            throw new IOException("KTX2 is VkFormat " + format
                    + ", which is neither Basis data nor RGBA8; GemRender cannot use it");
        }

        int width = texture.baseWidth();
        int height = texture.baseHeight();

        long offset;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pOffset = stack.mallocPointer(1);
            int found = Libktx.imageOffset(address, pOffset);
            if (found != Libktx.SUCCESS) {
                throw new IOException("KTX2 has no level 0: " + Libktx.errorString(found));
            }
            offset = pOffset.get(0);
        }

        ByteBuffer data = Libktx.data(texture);
        int bytes = width * height * BYTES_PER_PIXEL;
        if (data == null || data.capacity() < offset + bytes) {
            throw new IOException("KTX2 image data is shorter than its own " + width + "x" + height
                    + " header claims");
        }

        byte[] rgba = new byte[bytes];
        data.position((int) offset)
                .get(rgba);
        return new KtxImage(width, height, rgba);
    }
}
