package dev.golem.core.flywheel.indirect;

//? if neoforge && >=26.1 {

// Stride-aware wrapper over ResizableStorageBuffer with geometric growth. Used for
// element-count-addressable destination buffers (model meta, indirect commands, vis list)
// where capacity is requested in elements rather than bytes.
public final class ResizableStorageArray {

    private static final double DEFAULT_GROWTH_FACTOR = 1.25;

    private final ResizableStorageBuffer buffer;
    private final long stride;
    private final double growthFactor;
    private long capacity;

    public ResizableStorageArray(long stride) {
        this(stride, DEFAULT_GROWTH_FACTOR);
    }

    public ResizableStorageArray(long stride, double growthFactor) {
        if (stride <= 0L) throw new IllegalArgumentException("Stride must be positive");
        if (growthFactor <= 1.0) throw new IllegalArgumentException("Growth factor must be > 1");
        this.stride = stride;
        this.growthFactor = growthFactor;
        this.buffer = new ResizableStorageBuffer();
    }

    public int handle() {
        return buffer.handle();
    }

    public long stride() {
        return stride;
    }

    public long capacity() {
        return capacity;
    }

    public long byteCapacity() {
        return buffer.capacity();
    }

    public void ensureCapacity(long capacity) {
        if (capacity > this.capacity) {
            long grown = (long) Math.ceil(capacity * growthFactor);
            buffer.ensureCapacity(stride * grown);
            this.capacity = grown;
        }
    }

    public void delete() {
        buffer.delete();
    }
}
//?}
