package dev.golem.core.flywheel.indirect;

//? if >=26.1 {

import it.unimi.dsi.fastutil.ints.IntArrayList;

// Slot-index allocator over a fixed-stride backing buffer. Re-uses freed slots before
// extending past the high-water mark; subclasses provide the actual byte capacity and grow
// hook. Mirrors upstream Flywheel's AbstractArena.
public abstract class AbstractArena {

    protected final long elementSizeBytes;
    private final IntArrayList freeStack = new IntArrayList();
    private int top;

    protected AbstractArena(long elementSizeBytes) {
        this.elementSizeBytes = elementSizeBytes;
    }

    public int alloc() {
        if (!freeStack.isEmpty()) {
            return freeStack.popInt();
        }
        if ((long) top * elementSizeBytes >= byteCapacity()) {
            grow();
        }
        return top++;
    }

    public void free(int i) {
        freeStack.add(i);
    }

    public long byteOffsetOf(int i) {
        return (long) i * elementSizeBytes;
    }

    public int capacity() {
        return top;
    }

    public abstract long byteCapacity();

    protected abstract void grow();
}
//?}
