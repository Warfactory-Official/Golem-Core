package dev.golem.core.flywheel.indirect;

//? if >=26.1 {

import dev.golem.core.flywheel.Material;
import dev.golem.core.flywheel.gl.SlabBuffer;
import dev.golem.core.flywheel.instance.AbstractInstancer;
import dev.golem.core.flywheel.visual.DynamicVisual;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL45;

import java.util.Arrays;
import java.util.BitSet;

// SSBO-backed Instancer for the INDIRECT backend. Bytes live in a per-instancer persistent-
// mapped SlabBuffer (CPU setters write directly into the mapped pointer); the parent stage
// group's shared ObjectStorage owns the global page table that cull reads against. Upload is
// one glFlushMappedNamedBufferRange + glCopyNamedBufferSubData per dirty page run — no CPU
// staging ring, no scatter compute.
public final class IndirectInstancer extends AbstractInstancer {

    private final IndirectStageGroup group;

    private ObjectStorage.Mapping mapping;
    private int[] lastValidBits = new int[0];
    private final BitSet compactionDirtyPages = new BitSet();
    private int drawIndex = -1;
    private int baseInstance = -1;
    private int visibleCount;
    private int activePageCount;

    IndirectInstancer(int meshHandle, Material material, Identifier texture, IndirectStageGroup group) {
        super(meshHandle, material, texture);
        this.group = group;
    }

    public IndirectStageGroup group() {
        return group;
    }

    public int drawIndex() {
        return drawIndex;
    }

    public int baseInstance() {
        return baseInstance;
    }

    void attachMapping(ObjectStorage.Mapping mapping) {
        this.mapping = mapping;
    }

    ObjectStorage.Mapping mapping() {
        return mapping;
    }

    int livePageCount() {
        return activePageCount;
    }

    int visibleCount() {
        return visibleCount;
    }

    public int local2GlobalIndex(int localIndex) {
        return mapping.objectIndex2GlobalIndex(localIndex);
    }

    public int tryLocal2GlobalIndex(int localIndex) {
        int localPage = ObjectStorage.objectIndex2PageIndex(localIndex);
        if (localPage >= mapping.pageCount()) {
            return ObjectStorage.INVALID_PAGE;
        }
        int globalPage = mapping.globalPage(localPage);
        if (globalPage == ObjectStorage.INVALID_PAGE) {
            return ObjectStorage.INVALID_PAGE;
        }
        return (globalPage << ObjectStorage.LOG_2_PAGE_SIZE) + (localIndex & ObjectStorage.PAGE_MASK);
    }

    boolean hasVisibleInstances() {
        return visibleCount != 0;
    }

    void deactivate() {
        drawIndex = -1;
        baseInstance = -1;
        visibleCount = 0;
        activePageCount = 0;
        compactionDirtyPages.clear();
        if (mapping.pageCount() != 0) {
            mapping.updateCount(0);
        }
        Arrays.fill(lastValidBits, 0);
    }

    // Sync mapping page count + descriptor entries against the current live page count and
    // visibility snapshot. Returns the count of pages whose validity actually changed (used
    // by the caller to decide whether descriptor uploads need to run).
    int update(int newDrawIndex, int newBaseInstance) {
        boolean drawIndexChanged = drawIndex != newDrawIndex;
        drawIndex = newDrawIndex;
        baseInstance = newBaseInstance;

        int livePages = livePageCount();
        int oldPageCount = mapping.pageCount();
        if (oldPageCount != livePages) {
            mapping.updateCount(livePages);
        }
        if (drawIndexChanged && oldPageCount == 0 && livePages > 0) {
            compactionDirtyPages.set(0, livePages);
        }
        if (lastValidBits.length < livePages) {
            lastValidBits = Arrays.copyOf(lastValidBits, livePages);
        }

        int changes = 0;
        for (int p = 0; p < livePages; p++) {
            int currentBits = visibleMaskPage(p);
            if (drawIndexChanged || currentBits != lastValidBits[p]) {
                mapping.updatePage(p, drawIndex, currentBits);
                lastValidBits[p] = currentBits;
                changes++;
            }
        }
        Arrays.fill(lastValidBits, livePages, lastValidBits.length, 0);
        return changes;
    }

    @Override
    public void parallelUpdate(DynamicVisual.Context context) {
        compactVisiblePages();
    }

    // Walks the dirty/compacted local pages, merges consecutive mapped runs, and emits one
    // glFlushMappedNamedBufferRange + glCopyNamedBufferSubData per run. The slab buffer is
    // CPU-mapped persistent storage; setters wrote into it directly during the visual plan,
    // so the only "upload" is a GPU-to-GPU copy from the slab handle into the stage group's
    // shared object buffer.
    void uploadInstances(int dstVbo) {
        SlabBuffer slabBuffer = slabBuffer();
        slabBuffer.releaseRetired();

        int max = dirtyMax();
        long pageBytes = (long) ObjectStorage.PAGE_SIZE * STD140_BYTES;
        int pageMappingCount = mapping.pageCount();
        int firstPage = Integer.MAX_VALUE;
        int lastPage = -1;
        if (max >= 0) {
            int min = dirtyMin();
            max = Math.min(max, liveCount - 1);
            if (max >= min) {
                firstPage = min >> ObjectStorage.LOG_2_PAGE_SIZE;
                lastPage = max >> ObjectStorage.LOG_2_PAGE_SIZE;
            }
        }

        if (lastPage >= pageMappingCount) {
            lastPage = pageMappingCount - 1;
        }
        if (firstPage >= pageMappingCount) {
            firstPage = Integer.MAX_VALUE;
            lastPage = -1;
        }

        if (lastPage < 0 && compactionDirtyPages.isEmpty()) {
            clearDirty();
            return;
        }

        if (lastPage >= firstPage) {
            compactionDirtyPages.set(firstPage, lastPage + 1);
        }
        int slabHandle = slabBuffer.handle();
        int runStart = compactionDirtyPages.nextSetBit(0);
        while (runStart >= 0 && runStart < pageMappingCount) {
            int runEnd = Math.min(compactionDirtyPages.nextClearBit(runStart), pageMappingCount);
            int chunkStart = runStart;
            long prevDst = requireMappedPage(runStart);
            for (int p = runStart + 1; p < runEnd; p++) {
                long dst = requireMappedPage(p);
                if (dst != prevDst + pageBytes) {
                    uploadPageRun(slabHandle, dstVbo, chunkStart, p, pageBytes);
                    chunkStart = p;
                }
                prevDst = dst;
            }
            uploadPageRun(slabHandle, dstVbo, chunkStart, runEnd, pageBytes);
            runStart = compactionDirtyPages.nextSetBit(runEnd);
        }
        compactionDirtyPages.clear();
        clearDirty();
    }

    private void compactVisiblePages() {
        int pageLimit = (liveCount + ObjectStorage.PAGE_MASK) >> ObjectStorage.LOG_2_PAGE_SIZE;
        int srcPage = highestVisiblePage(pageLimit - 1);
        for (int dstPage = 0; dstPage < srcPage; dstPage++) {
            int dstBits = visibleMaskPage(dstPage);
            if (dstBits == -1) continue;
            while (srcPage > dstPage && dstBits != -1) {
                int srcBits = visibleMaskPage(srcPage);
                if (srcBits == 0) {
                    srcPage = highestVisiblePage(srcPage - 1);
                    continue;
                }
                int dstLocal = Integer.numberOfTrailingZeros(~dstBits);
                int srcLocal = 31 - Integer.numberOfLeadingZeros(srcBits);
                int dst = ObjectStorage.pageIndex2ObjectIndex(dstPage) + dstLocal;
                int src = ObjectStorage.pageIndex2ObjectIndex(srcPage) + srcLocal;
                swapSlots(dst, src);
                compactionDirtyPages.set(dstPage);
                compactionDirtyPages.set(srcPage);
                dstBits |= 1 << dstLocal;
                srcBits &= ~(1 << srcLocal);
                if (srcBits == 0) {
                    srcPage = highestVisiblePage(srcPage - 1);
                }
            }
        }
        refreshVisibleStats(pageLimit);
    }

    private int highestVisiblePage(int from) {
        for (int page = from; page >= 0; page--) {
            if (visibleMaskPage(page) != 0) {
                return page;
            }
        }
        return -1;
    }

    private void refreshVisibleStats(int pageLimit) {
        int visible = 0;
        int activePages = 0;
        for (int page = 0; page < pageLimit; page++) {
            int bits = visibleMaskPage(page);
            if (bits == 0) continue;
            visible += Integer.bitCount(bits);
            activePages = page + 1;
        }
        visibleCount = visible;
        activePageCount = activePages;
    }

    private long requireMappedPage(int page) {
        long dst = mapping.page2ByteOffset(page);
        if (dst < 0L) {
            throw new IllegalStateException("Active local page " + page + " is not mapped");
        }
        return dst;
    }

    private void uploadPageRun(int slabHandle, int dstVbo, int startPage, int endPageExclusive, long pageBytes) {
        long dstByteOff = requireMappedPage(startPage);
        long srcOff = (long) startPage * pageBytes;
        long srcEnd = Math.min((long) endPageExclusive * pageBytes, (long) liveCount * STD140_BYTES);
        long totalBytes = srcEnd - srcOff;
        // Setters wrote into the slab's mapped pointer; flush makes those CPU stores visible
        // to GL's command stream before the GPU-side copy reads them. CLIENT_STORAGE keeps the
        // backing CPU-resident so glCopyNamedBufferSubData is a fast DMA into the object VBO.
        // The final local page may be partial; flushing/copying page padding beyond liveCount
        // would exceed a small slab's allocation.
        GL45.glFlushMappedNamedBufferRange(slabHandle, srcOff, totalBytes);
        GL45.glCopyNamedBufferSubData(slabHandle, dstVbo, srcOff, dstByteOff, totalBytes);
    }

    @Override
    public void flush() {
        throw new IllegalStateException("IndirectInstancer must be flushed via IndirectStageGroup.upload()");
    }

    @Override
    public void close() {
        if (mapping != null) {
            mapping.delete();
            mapping = null;
        }
        lastValidBits = new int[0];
        drawIndex = -1;
        baseInstance = -1;
        visibleCount = 0;
        activePageCount = 0;
        compactionDirtyPages.clear();
        liveCount = 0;
        clearDirty();
        freeSlab();
    }
}
//?}
