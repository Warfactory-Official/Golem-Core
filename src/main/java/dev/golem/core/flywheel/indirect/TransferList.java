package dev.golem.core.flywheel.indirect;

//? if >=26.1 {

import org.lwjgl.system.MemoryUtil;

// Packed list of pending GPU copies. Each record is (int dstVbo, long srcOffset, long
// dstOffset, long size) at a 32-byte stride; push() coalesces with the previous record when
// it continues the same dst-VBO contiguously, which is the common case when uploading dirty
// page ranges to one destination buffer.
public final class TransferList {

    private static final long STRIDE = Long.BYTES * 4;

    private final MemoryBuffer block = new MemoryBuffer(STRIDE);
    private int length;

    public void push(int vbo, long srcOffset, long dstOffset, long size) {
        if (continuesLast(vbo, srcOffset, dstOffset)) {
            int last = length - 1;
            size(last, size(last) + size);
            return;
        }
        block.reallocIfNeeded(length);
        vbo(length, vbo);
        srcOffset(length, srcOffset);
        dstOffset(length, dstOffset);
        size(length, size);
        length++;
    }

    public int length() {
        return length;
    }

    public boolean isEmpty() {
        return length == 0;
    }

    public void reset() {
        length = 0;
    }

    public int vbo(int index) {
        return MemoryUtil.memGetInt(block.ptrForIndex(index));
    }

    public long srcOffset(int index) {
        return MemoryUtil.memGetLong(block.ptrForIndex(index) + Long.BYTES);
    }

    public long dstOffset(int index) {
        return MemoryUtil.memGetLong(block.ptrForIndex(index) + Long.BYTES * 2);
    }

    public long size(int index) {
        return MemoryUtil.memGetLong(block.ptrForIndex(index) + Long.BYTES * 3);
    }

    public void delete() {
        block.delete();
    }

    private boolean continuesLast(int vbo, long srcOffset, long dstOffset) {
        if (length == 0) return false;
        int last = length - 1;
        long lastSize = size(last);
        return vbo(last) == vbo
                && dstOffset(last) + lastSize == dstOffset
                && srcOffset(last) + lastSize == srcOffset;
    }

    private void vbo(int index, int vbo) {
        MemoryUtil.memPutInt(block.ptrForIndex(index), vbo);
    }

    private void srcOffset(int index, long srcOffset) {
        MemoryUtil.memPutLong(block.ptrForIndex(index) + Long.BYTES, srcOffset);
    }

    private void dstOffset(int index, long dstOffset) {
        MemoryUtil.memPutLong(block.ptrForIndex(index) + Long.BYTES * 2, dstOffset);
    }

    private void size(int index, long size) {
        MemoryUtil.memPutLong(block.ptrForIndex(index) + Long.BYTES * 3, size);
    }
}
//?}
