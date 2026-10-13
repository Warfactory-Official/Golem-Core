package dev.golem.core.flywheel.indirect;

//? if >=26.1 {

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL45;

// Driver-managed immutable-storage GPU buffer. Never CPU-mapped; uploads go through
// StagingBuffer (compute scatter), reads happen on the GPU. ensureCapacity reallocates and
// copies prior contents; the backing handle changes on grow.
public final class ResizableStorageBuffer {

    private int handle;
    private long capacity;

    public ResizableStorageBuffer() {
        handle = GL45.glCreateBuffers();
    }

    public int handle() {
        return handle;
    }

    public long capacity() {
        return capacity;
    }

    public void ensureCapacity(long capacity) {
        if (this.capacity >= capacity) return;
        if (this.capacity > 0L) {
            int oldHandle = handle;
            int newHandle = GL45.glCreateBuffers();
            GL45.glNamedBufferStorage(newHandle, capacity, 0);
            GL45.glCopyNamedBufferSubData(oldHandle, newHandle, 0L, 0L, this.capacity);
            GL15.glDeleteBuffers(oldHandle);
            handle = newHandle;
        } else {
            GL45.glNamedBufferStorage(handle, capacity, 0);
        }
        this.capacity = capacity;
    }

    public void delete() {
        if (handle != 0) {
            GL15.glDeleteBuffers(handle);
            handle = 0;
            capacity = 0L;
        }
    }
}
//?}
