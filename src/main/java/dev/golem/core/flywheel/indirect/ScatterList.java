package dev.golem.core.flywheel.indirect;

//? if >=26.1 {

import org.lwjgl.system.MemoryUtil;

// Packed scatter records consumed by the scatter compute shader. Each record is
// (sizeAndSrcOffset uint, dstOffset uint) — upper 8 bits of sizeAndSrcOffset carry the copy
// size in uints, lower 24 bits carry the source offset in uints (24 bits = 16 MB staging
// ring max). Large transfers are split into max-256-byte (64-uint) copies so each fits in one
// workgroup of the scatter shader (local_size_x = 64 with one uint per invocation).
//
// Matches CrankShaft / upstream Flywheel's ScatterList shape.
public final class ScatterList {

    public static final long STRIDE = Integer.BYTES * 2;
    private static final long MAX_BYTES_PER_SCATTER = 64L * Integer.BYTES;
    private final MemoryBuffer block = new MemoryBuffer(STRIDE);
    private int length;
    private long usedBytes;

    public void pushTransfer(TransferList transfers, int transferIndex) {
        long size = transfers.size(transferIndex);
        long srcOffset = transfers.srcOffset(transferIndex);
        long dstOffset = transfers.dstOffset(transferIndex);

        long offset = 0L;
        long remaining = size;
        while (remaining > 0L) {
            long copySize = Math.min(remaining, MAX_BYTES_PER_SCATTER);
            push(copySize, srcOffset + offset, dstOffset + offset);
            offset += copySize;
            remaining -= copySize;
        }
    }

    public void push(long sizeBytes, long srcOffsetBytes, long dstOffsetBytes) {
        block.reallocIfNeeded(length);
        long ptr = block.ptrForIndex(length);
        MemoryUtil.memPutInt(ptr, packSizeAndSrcOffset(sizeBytes, srcOffsetBytes));
        MemoryUtil.memPutInt(ptr + Integer.BYTES, (int) (dstOffsetBytes >>> 2));
        length++;
        usedBytes += STRIDE;
    }

    public int copyCount() {
        return length;
    }

    public long usedBytes() {
        return usedBytes;
    }

    public boolean isEmpty() {
        return length == 0;
    }

    public void reset() {
        length = 0;
        usedBytes = 0L;
    }

    public long ptr() {
        return block.ptr();
    }

    public void delete() {
        block.delete();
    }

    private static int packSizeAndSrcOffset(long sizeBytes, long srcOffsetBytes) {
        // Lower 24 bits: source offset in uints. Upper 8 bits: size in uints.
        int out = (int) (srcOffsetBytes >>> 2) & 0xFFFFFF;
        out |= (int) ((sizeBytes >>> 2) << 24) & 0xFF000000;
        return out;
    }
}
//?}
