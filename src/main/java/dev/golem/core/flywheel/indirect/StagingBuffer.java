package dev.golem.core.flywheel.indirect;

//? if >=26.1 {

import dev.golem.core.flywheel.gl.GlFence;
import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;

import java.util.function.LongConsumer;

// Persistent-mapped ring buffer feeding compute-driven scatter copies into destination VBOs.
//
// Per frame:
//   1. enqueueCopy / reserveForCopy memcpys CPU writes into the ring at `pos` and pushes a
//      TransferList record (dstVbo, srcOff, dstOff, size).
//   2. flush() emits one glFlushMappedNamedBufferRange over [start, pos), then dispatches
//      the scatter compute shader once per dst-VBO span (records covering different VBOs are
//      sorted by dstVbo at push time via TransferList's coalescing). One GlFence per flush
//      gates ring reclamation against in-flight GPU reads — matches CrankShaft / upstream.
//   3. reclaim() is called the next frame *before* writes; signaled fences return their
//      capacity to the available pool, which is the only thing keeping CPU writes from
//      racing GPU reads.
//
// The ring is the one and only CPU-mapped buffer in this pipeline; the actual destinations
// are driver-managed and never see CPU writes.
public final class StagingBuffer {

    private static final long DEFAULT_CAPACITY = 1024L * 1024L * 16L;
    private static final long SSBO_ALIGNMENT = GL45.glGetInteger(GL45.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);
    private static final int STORAGE_FLAGS =
            GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_WRITE_BIT | GL45.GL_CLIENT_STORAGE_BIT;
    private static final int MAP_FLAGS =
            GL45.GL_MAP_PERSISTENT_BIT
                    | GL45.GL_MAP_WRITE_BIT
                    | GL45.GL_MAP_FLUSH_EXPLICIT_BIT
                    | GL45.GL_MAP_INVALIDATE_BUFFER_BIT;

    private final IndirectScatterProgram scatterProgram;
    private final int vbo;
    private final long map;
    private final long capacity;

    private final OverflowStagingBuffer overflow = new OverflowStagingBuffer();
    private final TransferList transfers = new TransferList();
    private final PriorityQueue<FencedRegion> fencedRegions = new ObjectArrayFIFOQueue<>();
    private final ScatterBuffer scatterBuffer = new ScatterBuffer();
    private final ScatterList scatterList = new ScatterList();

    private long start;
    private long pos;
    private long usedCapacity;
    private long totalAvailable;

    private long scratchPtr;
    private long scratchSize;

    public StagingBuffer(IndirectScatterProgram scatterProgram) {
        this(DEFAULT_CAPACITY, scatterProgram);
    }

    public StagingBuffer(long capacity, IndirectScatterProgram scatterProgram) {
        this.capacity = capacity;
        this.scatterProgram = scatterProgram;
        vbo = GL45.glCreateBuffers();
        GL45.glNamedBufferStorage(vbo, capacity, STORAGE_FLAGS);
        map = GL45.nglMapNamedBufferRange(vbo, 0L, capacity, MAP_FLAGS);
        totalAvailable = capacity;
    }

    public void enqueueCopy(long size, int dstVbo, long dstOffset, LongConsumer write) {
        long direct = reserveForCopy(size, dstVbo, dstOffset);
        if (direct != MemoryUtil.NULL) {
            write.accept(direct);
            return;
        }
        long block = getScratch(size);
        write.accept(block);
        enqueueCopy(block, size, dstVbo, dstOffset);
    }

    public void enqueueCopy(long ptr, long size, int dstVbo, long dstOffset) {
        assertMultipleOf4(size);
        if (size > totalAvailable) {
            overflow.upload(ptr, size, dstVbo, dstOffset);
            return;
        }
        long remaining = capacity - pos;
        if (size > remaining) {
            long split = size - remaining;
            MemoryUtil.memCopy(ptr, map + pos, remaining);
            pushTransfer(dstVbo, pos, dstOffset, remaining);
            MemoryUtil.memCopy(ptr + remaining, map, split);
            pushTransfer(dstVbo, 0L, dstOffset + remaining, split);
            pos = split;
        } else {
            MemoryUtil.memCopy(ptr, map + pos, size);
            pushTransfer(dstVbo, pos, dstOffset, size);
            pos += size;
        }
    }

    public long reserveForCopy(long size, int dstVbo, long dstOffset) {
        assertMultipleOf4(size);
        long remaining = capacity - pos;
        if (size > remaining || size > totalAvailable) {
            return MemoryUtil.NULL;
        }
        long out = map + pos;
        pushTransfer(dstVbo, pos, dstOffset, size);
        pos += size;
        return out;
    }

    public long getScratch(long size) {
        if (scratchPtr == 0L) {
            scratchPtr = MemoryUtil.nmemAlloc(size);
            scratchSize = size;
        } else if (scratchSize < size) {
            scratchPtr = MemoryUtil.nmemRealloc(scratchPtr, size);
            scratchSize = size;
        }
        return scratchPtr;
    }

    public void flush() {
        if (transfers.isEmpty()) {
            return;
        }
        if (!scatterProgram.ensureLoaded()) {
            throw new IllegalStateException("Golem scatter compute shader is unavailable");
        }
        flushUsedRegion();
        dispatchComputeCopies();
        transfers.reset();
        fencedRegions.enqueue(new FencedRegion(new GlFence(), usedCapacity));
        usedCapacity = 0L;
        start = pos;
    }

    public void reclaim() {
        while (!fencedRegions.isEmpty()) {
            FencedRegion region = fencedRegions.first();
            if (!region.fence.isSignaled()) break;
            fencedRegions.dequeue();
            region.fence.delete();
            totalAvailable += region.capacity;
        }
    }

    public void delete() {
        GL45.glUnmapNamedBuffer(vbo);
        GL15.glDeleteBuffers(vbo);
        overflow.delete();
        scatterBuffer.delete();
        if (scratchPtr != 0L) {
            MemoryUtil.nmemFree(scratchPtr);
            scratchPtr = 0L;
            scratchSize = 0L;
        }
        transfers.delete();
        scatterList.delete();
        while (!fencedRegions.isEmpty()) {
            fencedRegions.dequeue().fence.delete();
        }
    }

    private void pushTransfer(int dstVbo, long srcOffset, long dstOffset, long size) {
        if (totalAvailable < size) {
            throw new IllegalStateException("Not enough available staging capacity");
        }
        transfers.push(dstVbo, srcOffset, dstOffset, size);
        usedCapacity += size;
        totalAvailable -= size;
    }

    private void dispatchComputeCopies() {
        scatterProgram.use();
        // Src binding (the ring) is invariant across dispatches.
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, vbo);
        int transferCount = transfers.length();
        for (int i = 0; i < transferCount; i++) {
            int dstVbo = transfers.vbo(i);
            scatterList.pushTransfer(transfers, i);
            int nextVbo = i == transferCount - 1 ? -1 : transfers.vbo(i + 1);
            if (dstVbo != nextVbo) {
                dispatchScatter(dstVbo);
            }
        }
    }

    private void dispatchScatter(int dstVbo) {
        long scatterSize = scatterList.usedBytes();
        long alignedPos = pos + SSBO_ALIGNMENT - 1L - (pos + SSBO_ALIGNMENT - 1L) % SSBO_ALIGNMENT;
        long remaining = capacity - alignedPos;
        long alignmentCost = alignedPos - pos;
        if (scatterSize <= remaining && scatterSize + alignmentCost <= totalAvailable) {
            // Fast path: write the scatter list straight into the ring at the next aligned
            // slot and bind it as binding 0. No second round-trip through scatterBuffer.
            MemoryUtil.memCopy(scatterList.ptr(), map + alignedPos, scatterSize);
            GL45.glFlushMappedNamedBufferRange(vbo, alignedPos, scatterSize);
            GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 0, vbo, alignedPos, scatterSize);
            usedCapacity += scatterSize + alignmentCost;
            totalAvailable -= scatterSize + alignmentCost;
            pos += scatterSize + alignmentCost;
        } else {
            // Slow path: stream the scatter list through its own buffer.
            scatterBuffer.upload(scatterList.ptr(), scatterSize);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, scatterBuffer.handle());
        }
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, dstVbo);
        GL43.glDispatchCompute(scatterList.copyCount(), 1, 1);
        scatterList.reset();
    }

    private static void assertMultipleOf4(long size) {
        if ((size & 3L) != 0L) {
            throw new IllegalArgumentException("Size must be a multiple of 4");
        }
    }

    private void flushUsedRegion() {
        if (pos < start) {
            // Wrapped — flush the tail half then the head half.
            GL45.glFlushMappedNamedBufferRange(vbo, start, capacity - start);
            GL45.glFlushMappedNamedBufferRange(vbo, 0L, pos);
        } else {
            GL45.glFlushMappedNamedBufferRange(vbo, start, pos - start);
        }
    }

    private record FencedRegion(GlFence fence, long capacity) {
    }

    private static final class OverflowStagingBuffer {

        private final int vbo;

        OverflowStagingBuffer() {
            vbo = GL45.glCreateBuffers();
        }

        void upload(long ptr, long size, int dstVbo, long dstOffset) {
            GL45.nglNamedBufferData(vbo, size, ptr, GL15.GL_STREAM_COPY);
            GL45.glCopyNamedBufferSubData(vbo, dstVbo, 0L, dstOffset, size);
        }

        void delete() {
            GL15.glDeleteBuffers(vbo);
        }
    }

    private static final class ScatterBuffer {

        private final int vbo;

        ScatterBuffer() {
            vbo = GL45.glCreateBuffers();
        }

        int handle() {
            return vbo;
        }

        void upload(long ptr, long size) {
            GL45.nglNamedBufferData(vbo, size, ptr, GL15.GL_STREAM_COPY);
        }

        void delete() {
            GL15.glDeleteBuffers(vbo);
        }
    }
}
//?}
