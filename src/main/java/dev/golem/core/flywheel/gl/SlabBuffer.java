package dev.golem.core.flywheel.gl;

//? if >=26.1 {

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL45;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Supplier;

// Persistent-mapped Mojang GpuBuffer that doubles as the instancer's CPU slab. Setters write
// directly into the mapped ByteBuffer; the instanced backend binds the buffer as a
// UBO via pass.setUniform("Instances", slabBuffer.gpuBuffer().slice(...)) without a CPU-to-GPU
// memcpy, and the indirect backend issues raw glCopyNamedBufferSubData from the slab handle
// into the shared object SSBO.
//
// The active device is OpenGL, so the AT-widened GlBuffer handle/persistentBuffer fields expose
// the native storage without a runtime interface injection.
//
// USAGE_HINT_CLIENT_STORAGE keeps the buffer in system RAM so reads stay RAM-speed; without it
// the driver may park the map in slow mapped VRAM. USAGE_MAP_READ is needed for swapSlots and
// writeStd140.
//
// ensureCapacity allocates a new buffer, CPU-memcpys the old mapped region into it (no GPU sync
// barrier) and retires the old GpuBuffer for two frames before close() unmaps and deletes. The
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
    private ByteBuffer mapped;
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

    public ByteBuffer mapped() {
        return mapped;
    }

    public long capacity() {
        return capacityBytes;
    }

    // Grow to at least requiredBytes. Returns true if the underlying buffer was replaced
    // (handle, GpuBuffer, and mapped pointer changed); callers must refresh any cached slab
    // address and re-upload the live range, prior GPU writes don't carry forward to the new
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
    // GpuBuffer retired two frames ago, by then the GPU has finished any reads from it (one
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
        mapped = null;
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
        Allocation allocation = create(bytes);
        gpuBuffer = allocation.buffer();
        handle = allocation.glBuffer().handle;
        mapped = allocation.mapped();
        capacityBytes = bytes;
    }

    private void grow(long newBytes) {
        Allocation allocation = create(newBytes);
        try {
            if (capacityBytes > 0L) {
                // Both mappings are CLIENT_STORAGE so this is RAM to RAM. The old buffer stays
                // alive for two more frames; the GPU and CPU only read it during this copy.
                ByteBuffer source = mapped.duplicate().order(ByteOrder.nativeOrder());
                source.position(0).limit((int) capacityBytes);
                ByteBuffer target = allocation.mapped().duplicate().order(ByteOrder.nativeOrder());
                target.position(0);
                target.put(source);
            }
        } catch (RuntimeException | Error failure) {
            allocation.buffer().close();
            throw failure;
        }

        if (retiredThisFrame != null) {
            retiredThisFrame.delete();
        }
        retiredThisFrame = new Retired(gpuBuffer);

        gpuBuffer = allocation.buffer();
        handle = allocation.glBuffer().handle;
        mapped = allocation.mapped();
        capacityBytes = newBytes;
    }

    private Allocation create(long bytes) {
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(label, USAGE_FLAGS, bytes);
        try {
            GlBuffer glBuffer = gl(buffer);
            return new Allocation(buffer, glBuffer, persistentBuffer(glBuffer));
        } catch (RuntimeException | Error failure) {
            buffer.close();
            throw failure;
        }
    }

    private static GlBuffer gl(GpuBuffer buffer) {
        if (buffer instanceof GlBuffer glBuffer) return glBuffer;
        // A device the capability probe let through but cannot serve: the whole engine stops, not
        // one visual, because every later allocation would fail the same way.
        throw new dev.golem.core.flywheel.EngineState.Unsupported("Golem flywheel requires the OpenGL render device");
    }

    private static ByteBuffer persistentBuffer(GpuBuffer buffer) {
        var mapped = gl(buffer).persistentBuffer;
        if (mapped == null) {
            throw new dev.golem.core.flywheel.EngineState.Unsupported("Golem flywheel requires a persistently mapped buffer");
        }
        return mapped.duplicate().order(ByteOrder.nativeOrder());
    }

    private record Allocation(GpuBuffer buffer, GlBuffer glBuffer, ByteBuffer mapped) {}

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
