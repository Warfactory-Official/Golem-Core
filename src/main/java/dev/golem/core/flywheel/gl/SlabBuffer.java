package dev.golem.core.flywheel.gl;

//? if neoforge && >=26.1 {

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;

import java.util.function.Supplier;

// Persistent-mapped Mojang GpuBuffer that doubles as the instancer's CPU slab. Setters write
// directly into the mapped pointer via Unsafe; the instanced backend binds the buffer as a
// UBO via pass.setUniform("Instances", slabBuffer.gpuBuffer().slice(...)) without a CPU→GPU
// memcpy, and the indirect backend issues raw glCopyNamedBufferSubData from the slab handle
// into the shared object SSBO.
//
// GpuBuffer's GL name and persistent map are exposed via GpuBufferExtension (interface
// injection in META-INF/interface_injection.json + AT widening of GlBuffer.handle/
// persistentBuffer), so SlabBuffer reads them directly off the GpuBuffer reference.
//
// USAGE flags:
//   USAGE_UNIFORM              — bound as UBO at draw time (instanced backend)
//   USAGE_COPY_SRC             — glCopyNamedBufferSubData source (indirect backend)
//   USAGE_MAP_WRITE            — persistent MAP_WRITE | MAP_PERSISTENT | MAP_FLUSH_EXPLICIT
//   USAGE_MAP_READ             — persistent MAP_READ; needed for swapSlots / writeStd140
//   USAGE_HINT_CLIENT_STORAGE  — driver hint to keep the buffer in system RAM so reads stay
//                                RAM-speed; without it, the driver may park the map in slow
//                                mapped-VRAM
//
// ensureCapacity allocates a new buffer at the requested byte size, CPU-memcpys the old
// mapped region into the new one (both client-storage RAM), and retires the old GpuBuffer for
// two frames before close() unmaps and deletes. CPU memcpy avoids any GPU sync barrier — the
// caller markFullyDirty's the live range and re-uploads through the new handle.
public final class SlabBuffer {

    private static final int USAGE_FLAGS =
            GpuBuffer.USAGE_UNIFORM
                    | GpuBuffer.USAGE_COPY_SRC
                    | GpuBuffer.USAGE_MAP_WRITE
                    | GpuBuffer.USAGE_MAP_READ
                    | GpuBuffer.USAGE_HINT_CLIENT_STORAGE;

    private final Supplier<String> label;
    private GpuBuffer gpuBuffer;
    private int handle;
    private long ptr;
    private long capacityBytes;

    private Retired retiredThisFrame;
    private Retired retiredLastFrame;

    public SlabBuffer(Supplier<String> label, long initialBytes) {
        this.label = label;
        allocate(initialBytes);
    }

    public GpuBuffer gpuBuffer() {
        return gpuBuffer;
    }

    public int handle() {
        return handle;
    }

    public long address() {
        return ptr;
    }

    public long capacity() {
        return capacityBytes;
    }

    // Grow to at least requiredBytes. Returns true if the underlying buffer was replaced
    // (handle, GpuBuffer, and mapped pointer changed); callers must refresh any cached slab
    // address and re-upload the live range — prior GPU writes don't carry forward to the new
    // handle since the new buffer is a fresh allocation.
    public boolean ensureCapacity(long requiredBytes) {
        if (requiredBytes <= capacityBytes) return false;
        grow(requiredBytes);
        return true;
    }

    public void flushRange(long byteOffset, long byteSize) {
        if (byteSize > 0L) {
            GL45.glFlushMappedNamedBufferRange(handle, byteOffset, byteSize);
        }
    }

    // Called once per frame on the render thread before any writes/uploads. Closes the
    // GpuBuffer retired two frames ago — by then the GPU has finished any reads from it (one
    // frame of in-flight commands, plus one frame of margin).
    public void releaseRetired() {
        if (retiredLastFrame != null) {
            retiredLastFrame.delete();
        }
        retiredLastFrame = retiredThisFrame;
        retiredThisFrame = null;
    }

    public void delete() {
        if (gpuBuffer != null) {
            gpuBuffer.close();
            gpuBuffer = null;
        }
        handle = 0;
        ptr = 0L;
        capacityBytes = 0L;
        if (retiredThisFrame != null) {
            retiredThisFrame.delete();
            retiredThisFrame = null;
        }
        if (retiredLastFrame != null) {
            retiredLastFrame.delete();
            retiredLastFrame = null;
        }
    }

    private void allocate(long bytes) {
        gpuBuffer = RenderSystem.getDevice().createBuffer(label, USAGE_FLAGS, bytes);
        handle = gpuBuffer.golem$glHandle();
        ptr = gpuBuffer.golem$persistentAddress();
        capacityBytes = bytes;
    }

    private void grow(long newBytes) {
        GpuBuffer newGpuBuffer = RenderSystem.getDevice().createBuffer(label, USAGE_FLAGS, newBytes);
        long newPtr = newGpuBuffer.golem$persistentAddress();

        if (capacityBytes > 0L) {
            // Both mappings are CLIENT_STORAGE so this is RAM→RAM. The old buffer stays
            // alive for two more frames (retired pattern) — the GPU may still be reading it,
            // but reading from CPU RAM concurrently with the CPU's memcpy source is a
            // read/read collision, not a race.
            MemoryUtil.memCopy(ptr, newPtr, capacityBytes);
        }

        if (retiredThisFrame != null) {
            retiredThisFrame.delete();
        }
        retiredThisFrame = new Retired(gpuBuffer);

        gpuBuffer = newGpuBuffer;
        handle = newGpuBuffer.golem$glHandle();
        ptr = newPtr;
        capacityBytes = newBytes;
    }

    private static final class Retired {

        private final GpuBuffer gpuBuffer;

        Retired(GpuBuffer gpuBuffer) {
            this.gpuBuffer = gpuBuffer;
        }

        void delete() {
            gpuBuffer.close();
        }
    }
}
//?}
