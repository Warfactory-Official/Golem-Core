package dev.golem.core.flywheel.indirect;

//? if >=26.1 {

import org.lwjgl.system.MemoryUtil;

// Growable native byte block addressed by fixed-stride records. Backs the packed transfer
// queue and scatter list. Both use ptr-style access (memGetInt / memPutInt at known
// offsets) and need a contiguous native buffer that can grow without copying through Java.
public final class MemoryBuffer {

    private final long stride;
    private long ptr;
    private long capacityBytes;

    public MemoryBuffer(long stride) {
        this.stride = stride;
    }

    public boolean reallocIfNeeded(int index) {
        long needed = neededCapacityForIndex(index);
        if (ptr == 0L) {
            long alloc = neededCapacityForIndex(index + 8);
            ptr = MemoryUtil.nmemAlloc(alloc);
            capacityBytes = alloc;
            return true;
        } else if (capacityBytes < needed) {
            long alloc = neededCapacityForIndex(index + 8);
            ptr = MemoryUtil.nmemRealloc(ptr, alloc);
            capacityBytes = alloc;
            return true;
        }
        return false;
    }

    public long ptr() {
        return ptr;
    }

    public long ptrForIndex(int index) {
        return ptr + index * stride;
    }

    public long neededCapacityForIndex(int index) {
        return (index + 1) * stride;
    }

    public void delete() {
        if (ptr != 0L) {
            MemoryUtil.nmemFree(ptr);
            ptr = 0L;
            capacityBytes = 0L;
        }
    }
}
//?}
