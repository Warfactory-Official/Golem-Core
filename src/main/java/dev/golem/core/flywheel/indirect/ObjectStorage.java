package dev.golem.core.flywheel.indirect;

//? if neoforge && >=26.1 {

import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;
import java.util.BitSet;

// Page-arena over a paired (objectBuffer, frameDescriptorBuffer) pair, mirroring upstream
// Flywheel's ObjectStorage. Per-instancer Mappings own local→global page tables; freed
// pages go on AbstractArena's free-list and get reused on the next alloc — sparse
// allocation. Freed-page descriptors are zeroed so the cull shader's validBits==0 lane mask
// causes its workgroup to exit immediately.
//
// objectBuffer        — instance data, PAGE_SIZE * elementSizeBytes per page
// frameDescriptorBuffer — (uint drawIndex, uint validBits) per page, consumed by golem_cull
// frameDescriptors    — CPU-side mirror of the descriptor buffer; uploaded through staging
//
// alloc / grow run from the per-stage prepareFrame worker (one worker per group, single-
// threaded against this storage). grow() only bumps CPU-side state — the GPU buffers are
// grown later by ensureBufferCapacity() on the render thread.
public final class ObjectStorage extends AbstractArena {

    public static final int LOG_2_PAGE_SIZE = 5;
    public static final int PAGE_SIZE = 1 << LOG_2_PAGE_SIZE;
    public static final int PAGE_MASK = PAGE_SIZE - 1;
    public static final int INVALID_PAGE = -1;
    public static final int INITIAL_PAGES_ALLOCATED = 4;
    public static final int DESCRIPTOR_SIZE_BYTES = Integer.BYTES * 2;

    public final ResizableStorageBuffer objectBuffer = new ResizableStorageBuffer();
    public final ResizableStorageBuffer frameDescriptorBuffer = new ResizableStorageBuffer();

    private final BitSet changedFrames = new BitSet();
    private long descriptorPtr;
    private long descriptorCapacityBytes;
    private long logicalByteCapacity;
    private long requiredDescriptorBytes;

    public ObjectStorage(long objectSizeBytes) {
        super((long) PAGE_SIZE * objectSizeBytes);
        long initialObjectBytes = (long) INITIAL_PAGES_ALLOCATED * elementSizeBytes;
        long initialDescriptorBytes = (long) INITIAL_PAGES_ALLOCATED * DESCRIPTOR_SIZE_BYTES;
        objectBuffer.ensureCapacity(initialObjectBytes);
        frameDescriptorBuffer.ensureCapacity(initialDescriptorBytes);
        logicalByteCapacity = initialObjectBytes;
        descriptorCapacityBytes = initialDescriptorBytes;
        requiredDescriptorBytes = initialDescriptorBytes;
        descriptorPtr = MemoryUtil.nmemAlloc(descriptorCapacityBytes);
        MemoryUtil.memSet(descriptorPtr, 0, descriptorCapacityBytes);
    }

    public Mapping createMapping() {
        return new Mapping();
    }

    @Override
    public long byteCapacity() {
        return logicalByteCapacity;
    }

    @Override
    public void free(int i) {
        if (i == INVALID_PAGE) return;
        super.free(i);
        // Zero the descriptor so cull's `(validBits & laneBit) == 0` exit fires for every
        // lane of the freed page's workgroup. The descriptor stays zeroed until alloc()
        // hands the index back out and Mapping.set rewrites it.
        long ptr = ptrForPage(i);
        MemoryUtil.memPutInt(ptr, 0);
        MemoryUtil.memPutInt(ptr + Integer.BYTES, 0);
        changedFrames.set(i);
    }

    private void set(int i, int drawIndex, int validBits) {
        long ptr = ptrForPage(i);
        MemoryUtil.memPutInt(ptr, drawIndex);
        MemoryUtil.memPutInt(ptr + Integer.BYTES, validBits);
        changedFrames.set(i);
    }

    @Override
    protected void grow() {
        long oldBytes = logicalByteCapacity;
        long newBytes = Math.max(oldBytes * 2L, elementSizeBytes);
        logicalByteCapacity = newBytes;
        long newPages = newBytes / elementSizeBytes;
        long newDescriptorBytes = newPages * DESCRIPTOR_SIZE_BYTES;
        if (newDescriptorBytes > descriptorCapacityBytes) {
            descriptorPtr = MemoryUtil.nmemRealloc(descriptorPtr, newDescriptorBytes);
            MemoryUtil.memSet(descriptorPtr + descriptorCapacityBytes, 0, newDescriptorBytes - descriptorCapacityBytes);
            descriptorCapacityBytes = newDescriptorBytes;
        }
        requiredDescriptorBytes = newDescriptorBytes;
    }

    public void ensureBufferCapacity() {
        objectBuffer.ensureCapacity(logicalByteCapacity);
        frameDescriptorBuffer.ensureCapacity(requiredDescriptorBytes);
    }

    public void uploadDescriptors(StagingBuffer stagingBuffer) {
        if (changedFrames.isEmpty()) return;
        int limit = capacity();
        int start = changedFrames.nextSetBit(0);
        while (start >= 0 && start < limit) {
            int end = Math.min(changedFrames.nextClearBit(start), limit);
            long offset = (long) start * DESCRIPTOR_SIZE_BYTES;
            long bytes = (long) (end - start) * DESCRIPTOR_SIZE_BYTES;
            stagingBuffer.enqueueCopy(descriptorPtr + offset, bytes, frameDescriptorBuffer.handle(), offset);
            start = changedFrames.nextSetBit(end);
        }
        changedFrames.clear();
    }

    public void delete() {
        objectBuffer.delete();
        frameDescriptorBuffer.delete();
        if (descriptorPtr != 0L) {
            MemoryUtil.nmemFree(descriptorPtr);
            descriptorPtr = 0L;
            descriptorCapacityBytes = 0L;
        }
    }

    private long ptrForPage(int page) {
        return descriptorPtr + (long) page * DESCRIPTOR_SIZE_BYTES;
    }

    public static int objectIndex2PageIndex(int objectIndex) {
        return objectIndex >> LOG_2_PAGE_SIZE;
    }

    public static int pageIndex2ObjectIndex(int pageIndex) {
        return pageIndex << LOG_2_PAGE_SIZE;
    }

    // Per-instancer page table. The instancer addresses pages by local index (0..N); the
    // global page indices it gets back from the arena are stable for the page's lifetime
    // (free returns the index to the free-list, never relocates an existing page).
    public final class Mapping {

        private static final int[] EMPTY_ALLOCATION = new int[0];

        private int[] pages = EMPTY_ALLOCATION;

        public void updatePage(int index, int drawIndex, int validBits) {
            if (validBits == 0) {
                holePunch(index);
                return;
            }
            int frame = pages[index];
            if (frame == INVALID_PAGE) {
                frame = unHolePunch(index);
            }
            ObjectStorage.this.set(frame, drawIndex, validBits);
        }

        public void holePunch(int index) {
            int page = pages[index];
            if (page == INVALID_PAGE) return;
            ObjectStorage.this.free(page);
            pages[index] = INVALID_PAGE;
        }

        private int unHolePunch(int index) {
            int page = ObjectStorage.this.alloc();
            pages[index] = page;
            return page;
        }

        public void updateCount(int newLength) {
            int oldLength = pages.length;
            if (oldLength > newLength) {
                shrink(oldLength, newLength);
            } else if (oldLength < newLength) {
                grow(newLength, oldLength);
            }
        }

        public int pageCount() {
            return pages.length;
        }

        public long page2ByteOffset(int index) {
            int page = pages[index];
            if (page == INVALID_PAGE) return -1L;
            return ObjectStorage.this.byteOffsetOf(page);
        }

        public int globalPage(int index) {
            return pages[index];
        }

        public void delete() {
            for (int i = pages.length - 1; i >= 0; i--) {
                holePunch(i);
            }
            pages = EMPTY_ALLOCATION;
        }

        public int objectIndex2GlobalIndex(int objectIndex) {
            int page = pages[objectIndex2PageIndex(objectIndex)];
            if (page == INVALID_PAGE) {
                throw new IllegalStateException("Local object page is not currently mapped");
            }
            return (page << LOG_2_PAGE_SIZE) + (objectIndex & PAGE_MASK);
        }

        private void grow(int neededPages, int oldLength) {
            pages = Arrays.copyOf(pages, neededPages);
            for (int i = oldLength; i < neededPages; i++) {
                pages[i] = INVALID_PAGE;
            }
        }

        private void shrink(int oldLength, int neededPages) {
            for (int i = oldLength - 1; i >= neededPages; i--) {
                holePunch(i);
            }
            pages = Arrays.copyOf(pages, neededPages);
        }
    }
}
//?}
