package dev.golem.core.flywheel.instance;

//? if neoforge && >=26.1 {

import dev.golem.core.flywheel.gl.UnsafeHolder;
import dev.golem.core.flywheel.Instancer;
import dev.golem.core.flywheel.Material;
import dev.golem.core.flywheel.gl.SlabBuffer;
import dev.golem.core.flywheel.visual.DynamicVisual;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;

import static dev.golem.core.flywheel.gl.UnsafeHolder.U;

// Per-bucket persistent CPU staging shared by both backends. Subclasses (InstancedInstancer
// for UBO, IndirectInstancer for SSBO) own the GPU-side specifics: where the bytes go on
// flush, how growth is handled, when the GPU buffer gets created. Visuals only see the
// Instancer interface — the public allocate() returns an Instance handle.
//
// std140 layout per instance (112 bytes):
//   ivec2 OverlayCoords  (offset 0)
//   ivec2 LightCoords    (offset 8)
//   vec4  Color          (offset 16)
//   mat4  ModelMat       (offset 32, ends at 96)
//   vec4  UVTransform    (offset 96, ends at 112) — (offsetU, offsetV, scaleU, scaleV)
//
// UVTransform applies in the vertex shader as `texCoord0 = UV0 * scale + offset`. Identity
// is (0, 0, 1, 1) so visuals that don't animate UVs see no behavior change. Sprite-strip
// animations (rad fog frame cycle, RBMK fire/steam/mush, MukeCloud) use this to pick a
// single sub-rect of an N×M frame sheet per frame.
//
// Storage: a single persistent-mapped GL buffer of `capacity * 112` bytes in std140 layout is
// the source of truth. Setters write directly into the mapped pointer via Unsafe; the indirect
// backend uploads dirty pages with one glFlushMappedNamedBufferRange + glCopyNamedBufferSubData
// into the shared object SSBO — no CPU staging ring, no scatter compute. The slab is created
// by SlabBuffer in the constructor (render thread) and freed in close(); ensureCapacity
// reallocates via SlabBuffer's retired-frame pattern and zeros the new tail. Two heap-side
// caches survive only because they cross-influence each other:
//   - colors[] is needed so setVisible(true) can restore the prior color into the slab,
//     since the slab's color slot is zeroed when invisible (the GPU cull pass uses an
//     all-zero color as the dead-instance sentinel).
//   - visible[] is needed so setColor knows whether to overwrite the slab's color slot
//     (only when visible) or just update the cache and leave the sentinel in place.
//
// Threading: the per-slot writers (writeTransform / writeLight / writeOverlay / writeColor /
// writeVisible / writeUV) are lock-free. Each Instance handle owns a unique slot index, so
// concurrent calls from worker threads target disjoint slab regions and disjoint cache
// slots. Two pieces of state are shared and need explicit synchronization:
//   - The dirty range is sharded across SHARD_COUNT cache-line-padded (min, max) cells.
//     Each markDirty hashes the calling thread to a shard, so K worker threads contend only
//     when they collide on a shard (~K/SHARD_COUNT chance per pair). dirtyMin/Max reduce all
//     shards on the render thread at flush time.
//   - visiblePageMasks packs 32 slots into one int word, so two workers updating different
//     slots in the same word would race a plain |=/&=. Bit set/clear go through
//     Unsafe.compareAndSetInt, mirroring upstream Flywheel's per-page AtomicInteger
//     validity bitset without the AtomicIntegerArray pointer indirection.
// The remaining structural methods (allocate, free, ensureCapacity, flush, writeStd140,
// close) run outside visual writer tasks. Backend parallelUpdate may reshuffle slots only
// after the visual frame plans have completed.
public abstract class AbstractInstancer implements Instancer {

    public static final int FLOATS_PER_MAT4 = 16;
    public static final int STD140_BYTES = 112;
    public static final int FLOATS_PER_UV = 4;
    private static final int INITIAL_CAPACITY = 16;

    // Field byte offsets within a std140 record.
    private static final int OFF_OVERLAY = 0;
    private static final int OFF_LIGHT = 8;
    private static final int OFF_COLOR = 16;
    private static final int OFF_MAT = 32;
    private static final int OFF_UV = 96;

    protected final int meshHandle;
    protected final Material material;
    protected final Identifier texture;

    // Native std140 slab. slabBuffer is a persistent-mapped GL buffer; slabAddr is its current
    // mapped pointer (refreshed on every grow). Capacity is tracked in slots (slab byte size =
    // capacity * 112). swapScratchAddr is a small Unsafe-allocated scratch for slot swaps and
    // doesn't need GL backing.
    private final SlabBuffer slabBuffer;
    protected long slabAddr;
    private long swapScratchAddr;
    protected int capacity;

    // Side caches — only the two fields whose semantics depend on each other (see class doc).
    protected int[] colors;
    protected boolean[] visible;
    // Packed 32-slot visibility masks for backends that batch work per fixed-size page.
    // Mirrors upstream Flywheel's per-page AtomicInteger validity bitset: writeVisible runs
    // on workers in the parallel frame phase, so two workers updating different slots in the
    // same 32-slot word race the read-modify-write. Mutated via Unsafe.compareAndSetInt to
    // avoid AtomicIntegerArray's pointer indirection.
    private int[] visiblePageMasks;

    protected int liveCount;
    private InstanceHandle[] handles;

    private static final int SHARD_COUNT;
    private static final int SHARD_MASK;
    private static final int SHARD_STRIDE = 8;
    private static final int MIN_OFF_IN_SHARD = 2;
    private static final int MAX_OFF_IN_SHARD = 4;
    static {
        int parallelism = ForkJoinPool.commonPool().getParallelism();
        int n = parallelism > 1 ? Integer.highestOneBit(parallelism - 1) << 1 : 4;
        SHARD_COUNT = Math.max(4, n);
        SHARD_MASK = SHARD_COUNT - 1;
    }
    private final long[] dirtyShards = new long[SHARD_COUNT * SHARD_STRIDE];

    protected AbstractInstancer(int meshHandle, Material material, Identifier texture) {
        this.meshHandle = meshHandle;
        this.material = material;
        this.texture = texture;
        this.capacity = INITIAL_CAPACITY;
        long initialBytes = (long) INITIAL_CAPACITY * STD140_BYTES;
        this.slabBuffer = new SlabBuffer(
                () -> "Golem Slab " + material.name() + " mesh=" + meshHandle,
                initialBytes);
        this.slabAddr = slabBuffer.address();
        this.swapScratchAddr = U.allocateMemory(STD140_BYTES);
        // glNamedBufferStorage doesn't zero; CPU-zero the mapped region so unwritten slots
        // hold the dead-instance sentinel (all-zero color etc.).
        U.setMemory(slabAddr, initialBytes, (byte) 0);
        this.colors = new int[INITIAL_CAPACITY];
        Arrays.fill(colors, 0xFFFFFFFF);
        this.visible = new boolean[INITIAL_CAPACITY];
        this.handles = new InstanceHandle[INITIAL_CAPACITY];
        this.visiblePageMasks = new int[(INITIAL_CAPACITY + 31) >> 5];
        // Plain stores; final-field publication of `this` makes them visible to every
        // thread that later observes a reference to this instancer.
        for (int s = 0; s < SHARD_COUNT; s++) {
            dirtyShards[s * SHARD_STRIDE + MIN_OFF_IN_SHARD] = Integer.MAX_VALUE;
            dirtyShards[s * SHARD_STRIDE + MAX_OFF_IN_SHARD] = -1;
        }
    }

    public final int meshHandle() {
        return meshHandle;
    }

    public final Material material() {
        return material;
    }

    public final Identifier texture() {
        return texture;
    }

    public final int liveCount() {
        return liveCount;
    }

    @Override
    public Instance allocate() {
        ensureCapacity(liveCount + 1);
        int index = liveCount++;
        visible[index] = true;
        colors[index] = 0xFFFFFFFF;
        atomicSetBit(visiblePageMasks, index >> 5, 1 << (index & 31));
        long base = slabAddr + (long) index * STD140_BYTES;
        // Reset to defaults: zero everything, then write the non-zero values. setMemory of
        // 112 bytes is one intrinsic memset; the targeted putFloats afterwards are the
        // diagonal of mat4, the white color, and the identity UV scale.
        U.setMemory(base, STD140_BYTES, (byte) 0);
        U.putFloat(base + OFF_COLOR,      1F);
        U.putFloat(base + OFF_COLOR + 4,  1F);
        U.putFloat(base + OFF_COLOR + 8,  1F);
        U.putFloat(base + OFF_COLOR + 12, 1F);
        U.putFloat(base + OFF_MAT,        1F);
        U.putFloat(base + OFF_MAT + 20,   1F);
        U.putFloat(base + OFF_MAT + 40,   1F);
        U.putFloat(base + OFF_MAT + 60,   1F);
        U.putFloat(base + OFF_UV + 8,     1F);
        U.putFloat(base + OFF_UV + 12,    1F);
        InstanceHandle handle = new InstanceHandle(this, index);
        handles[index] = handle;
        markDirty(index);
        return handle;
    }

    void free(InstanceHandle handle) {
        int index = handle.index();
        int last = liveCount - 1;
        if (index != last) {
            moveSlot(last, index);
            markDirty(index);
        }
        clearSlot(last);
        handle.moveTo(-1);
        liveCount = last;
    }

    void writeTransform(int index, Matrix4fc m) {
        // JOML writes the column-major mat4 directly to the slab via Unsafe.
        m.getToAddress(slabAddr + (long) index * STD140_BYTES + OFF_MAT);
        markDirty(index);
    }

    void writeLight(int index, int light) {
        // Pack the two uint16 channels (block, sky) into the std140 ivec2: an int per
        // 4 bytes. Bottom 16 bits at +0, top 16 at +4 of the ivec2's 8-byte slot.
        long base = slabAddr + (long) index * STD140_BYTES + OFF_LIGHT;
        U.putInt(base,     light & 0xFFFF);
        U.putInt(base + 4, light >>> 16 & 0xFFFF);
        markDirty(index);
    }

    void writeOverlay(int index, int overlay) {
        long base = slabAddr + (long) index * STD140_BYTES + OFF_OVERLAY;
        U.putInt(base,     overlay & 0xFFFF);
        U.putInt(base + 4, overlay >>> 16 & 0xFFFF);
        markDirty(index);
    }

    void writeColor(int index, int color) {
        colors[index] = color;
        // Only blast over the slab color when visible — otherwise the dead-instance
        // sentinel (all-zero color) must remain in place.
        if (visible[index]) {
            long base = slabAddr + (long) index * STD140_BYTES + OFF_COLOR;
            U.putFloat(base,      ARGB.redFloat(color));
            U.putFloat(base + 4,  ARGB.greenFloat(color));
            U.putFloat(base + 8,  ARGB.blueFloat(color));
            U.putFloat(base + 12, ARGB.alphaFloat(color));
        }
        markDirty(index);
    }

    // Raw-float writer for materials that hijack InstanceColor as per-instance numerical data
    // (slide offsets for CUTOUT_CLIP_SLAB / CUTOUT_CLIP_HALFSPACE, etc.). Bypasses the ARGB→byte→float round-trip
    // so the values land in the slab with full float precision. Skips the colors[] cache —
    // setVisible(true) would restore the cached int form (lossy), so callers using rawColor
    // must re-write each frame or avoid setVisible toggles. Visibility check is the same as
    // writeColor: silently ignored on invisible slots so the dead-instance sentinel stays.
    void writeRawColor(int index, float r, float g, float b, float a) {
        if (!visible[index]) return;
        long base = slabAddr + (long) index * STD140_BYTES + OFF_COLOR;
        U.putFloat(base,      r);
        U.putFloat(base + 4,  g);
        U.putFloat(base + 8,  b);
        U.putFloat(base + 12, a);
        markDirty(index);
    }

    void writeVisible(int index, boolean v) {
        visible[index] = v;
        int bit = 1 << (index & 31);
        int word = index >> 5;
        if (v) {
            atomicSetBit(visiblePageMasks, word, bit);
        } else {
            atomicClearBit(visiblePageMasks, word, bit);
        }
        long base = slabAddr + (long) index * STD140_BYTES + OFF_COLOR;
        if (v) {
            int color = colors[index];
            U.putFloat(base,      ARGB.redFloat(color));
            U.putFloat(base + 4,  ARGB.greenFloat(color));
            U.putFloat(base + 8,  ARGB.blueFloat(color));
            U.putFloat(base + 12, ARGB.alphaFloat(color));
        } else {
            U.putLong(base,     0L);
            U.putLong(base + 8, 0L);
        }
        markDirty(index);
    }

    void writeUV(int index, float offsetU, float offsetV, float scaleU, float scaleV) {
        long base = slabAddr + (long) index * STD140_BYTES + OFF_UV;
        U.putFloat(base,      offsetU);
        U.putFloat(base + 4,  offsetV);
        U.putFloat(base + 8,  scaleU);
        U.putFloat(base + 12, scaleV);
        markDirty(index);
    }

    // Bulk-copy one slot's std140 record into dst at dstOffset. Used by CrumblingScratch,
    // which stitches instances from multiple instancers (different slabs) into one transient
    // UBO. Single-instance flushes go directly via slabAddress() on the subclass — there's
    // no per-instance pack work anymore.
    public void writeStd140(int index, ByteBuffer dst, int dstOffset) {
        long destAddr = MemoryUtil.memAddress(dst) + dstOffset;
        U.copyMemory(slabAddr + (long) index * STD140_BYTES, destAddr, STD140_BYTES);
    }

    // Native address of the std140 slab — currently the persistent-mapped pointer of
    // slabBuffer. Refreshed by ensureCapacity when the underlying buffer grows.
    public final long slabAddress() {
        return slabAddr;
    }

    // The GL handle of the slab buffer. Indirect backend uses this as the
    // glCopyNamedBufferSubData source when pushing dirty pages into the shared object buffer.
    public final int slabHandle() {
        return slabBuffer.handle();
    }

    // Persistent-mapped slab backing. Subclasses call slabBuffer.flushRange before issuing a
    // GPU read, and slabBuffer.releaseRetired once per frame on the render thread.
    public final SlabBuffer slabBuffer() {
        return slabBuffer;
    }

    // Lock-free CAS loop. Workers from the parallel frame phase concurrently mark slots
    // dirty; each call lowers its shard's min to `index` if smaller, raises its shard's
    // max if larger. Shard chosen by hashing the calling thread, so K workers spread
    // across SHARD_COUNT cells with low collision probability. getLongOpaque inside the
    // loop is sufficient — compareAndSetLong re-validates the value and carries volatile
    // (release+acquire) semantics on success.
    protected final void markDirty(int index) {
        int base = (Thread.currentThread().hashCode() & SHARD_MASK) * SHARD_STRIDE;
        long minOff = UnsafeHolder.offLong(base + MIN_OFF_IN_SHARD);
        long maxOff = UnsafeHolder.offLong(base + MAX_OFF_IN_SHARD);
        long v = index;
        long cur;
        do {
            cur = U.getLongOpaque(dirtyShards, minOff);
            if (v >= cur) break;
        } while (!U.compareAndSetLong(dirtyShards, minOff, cur, v));
        do {
            cur = U.getLongOpaque(dirtyShards, maxOff);
            if (v <= cur) break;
        } while (!U.compareAndSetLong(dirtyShards, maxOff, cur, v));
    }

    // Render-thread reads — reduce all shards into a global (min, max). Acquire ensures we
    // observe every per-slot store published by the workers before they entered the
    // dirty-range CAS. (In practice the frame future's join already gives us this
    // happens-before; getLongAcquire just makes it explicit and free.) O(SHARD_COUNT)
    // acquire-loads at flush time — ~16 ns total.
    public final int dirtyMin() {
        long min = Integer.MAX_VALUE;
        for (int s = 0; s < SHARD_COUNT; s++) {
            long v = U.getLongAcquire(dirtyShards, UnsafeHolder.offLong(s * SHARD_STRIDE + MIN_OFF_IN_SHARD));
            if (v < min) min = v;
        }
        return (int) min;
    }

    public final int dirtyMax() {
        long max = -1L;
        for (int s = 0; s < SHARD_COUNT; s++) {
            long v = U.getLongAcquire(dirtyShards, UnsafeHolder.offLong(s * SHARD_STRIDE + MAX_OFF_IN_SHARD));
            if (v > max) max = v;
        }
        return (int) max;
    }

    // Force the entire live range to be dirty. Subclasses call this after growing GPU
    // capacity, since the new buffer needs every slot re-uploaded. Render-thread only —
    // writes the full range into shard 0; the dirtyMin/Max reduce will pick it up.
    protected final void markFullyDirty() {
        if (liveCount > 0) {
            U.putLongRelease(dirtyShards, UnsafeHolder.offLong(MIN_OFF_IN_SHARD), 0L);
            U.putLongRelease(dirtyShards, UnsafeHolder.offLong(MAX_OFF_IN_SHARD), (long) (liveCount - 1));
        }
    }

    protected final void clearDirty() {
        for (int s = 0; s < SHARD_COUNT; s++) {
            U.putLongRelease(dirtyShards, UnsafeHolder.offLong(s * SHARD_STRIDE + MIN_OFF_IN_SHARD), Integer.MAX_VALUE);
            U.putLongRelease(dirtyShards, UnsafeHolder.offLong(s * SHARD_STRIDE + MAX_OFF_IN_SHARD), -1L);
        }
    }

    protected final void markPageDirty(int page) {
        if (liveCount == 0) return;
        int start = page << 5;
        if (start >= liveCount) return;
        int end = Math.min(start + 31, liveCount - 1);
        markDirty(start);
        if (end != start) {
            markDirty(end);
        }
    }

    protected final int visibleMaskPage(int page) {
        if (page >= visiblePageMasks.length) return 0;
        return U.getIntAcquire(visiblePageMasks, UnsafeHolder.offInt(page));
    }

    private static void atomicSetBit(int[] arr, int word, int bit) {
        long off = UnsafeHolder.offInt(word);
        int cur;
        do {
            cur = U.getIntOpaque(arr, off);
            if ((cur & bit) != 0) return;
        } while (!U.compareAndSetInt(arr, off, cur, cur | bit));
    }

    private static void atomicClearBit(int[] arr, int word, int bit) {
        long off = UnsafeHolder.offInt(word);
        int cur;
        do {
            cur = U.getIntOpaque(arr, off);
            if ((cur & bit) == 0) return;
        } while (!U.compareAndSetInt(arr, off, cur, cur & ~bit));
    }

    // Protected so backend-specific instancers can react when CPU capacity grows (e.g. stage-
    // local page-mapping growth on the INDIRECT path). Render-thread only — slabBuffer.grow
    // creates a new GL buffer and remaps; workers writing to the slab during this call would
    // race. The existing call sites (allocate) run from visual construction at frame
    // boundaries, so this constraint already holds.
    protected void ensureCapacity(int required) {
        int cap = capacity;
        if (required <= cap) return;
        int next = cap << 1;
        while (next < required) next <<= 1;
        long newSize = (long) next * STD140_BYTES;
        long oldSize = (long) cap * STD140_BYTES;
        boolean grew = slabBuffer.ensureCapacity(newSize);
        if (grew) {
            slabAddr = slabBuffer.address();
            // The new GPU handle has no contents — slabBuffer's grow CPU-memcpys old-mapped
            // into new-mapped for CPU continuity, but the destination object buffer the
            // indirect backend copies into doesn't yet have a glCopyNamedBufferSubData from
            // the new handle. Re-flag the live range so the next upload pushes everything.
            markFullyDirty();
        }
        // Zero the tail so unallocated slots stay zero (dead-instance sentinel). Always
        // runs — even if the buffer didn't physically grow we still expanded the slot range.
        U.setMemory(slabAddr + oldSize, newSize - oldSize, (byte) 0);
        colors = copyAndFill(colors, next, 0xFFFFFFFF);
        visible = Arrays.copyOf(visible, next);
        handles = Arrays.copyOf(handles, next);
        visiblePageMasks = Arrays.copyOf(visiblePageMasks, (next + 31) >> 5);
        capacity = next;
    }

    public final int cpuCapacity() {
        return capacity;
    }

    public void parallelUpdate(DynamicVisual.Context context) {
    }

    private static int[] copyAndFill(int[] src, int newLen, int fill) {
        int[] dst = Arrays.copyOf(src, newLen);
        Arrays.fill(dst, src.length, newLen, fill);
        return dst;
    }

    public abstract void flush();

    public abstract void close();

    // Subclasses must call this from their close() override after releasing the GPU buffer.
    protected final void freeSlab() {
        slabBuffer.delete();
        slabAddr = 0L;
        if (swapScratchAddr != 0L) {
            U.freeMemory(swapScratchAddr);
            swapScratchAddr = 0L;
        }
    }

    protected final void swapSlots(int a, int b) {
        if (a == b) return;

        long aBase = slabAddr + (long) a * STD140_BYTES;
        long bBase = slabAddr + (long) b * STD140_BYTES;

        U.copyMemory(aBase, swapScratchAddr, STD140_BYTES);
        U.copyMemory(bBase, aBase, STD140_BYTES);
        U.copyMemory(swapScratchAddr, bBase, STD140_BYTES);

        int color = colors[a];
        colors[a] = colors[b];
        colors[b] = color;

        boolean wasVisible = visible[a];
        visible[a] = visible[b];
        visible[b] = wasVisible;

        InstanceHandle handle = handles[a];
        handles[a] = handles[b];
        handles[b] = handle;
        handles[a].moveTo(a);
        handles[b].moveTo(b);

        updateVisibleBit(a);
        updateVisibleBit(b);
    }

    private void moveSlot(int src, int dst) {
        long srcBase = slabAddr + (long) src * STD140_BYTES;
        long dstBase = slabAddr + (long) dst * STD140_BYTES;
        U.copyMemory(srcBase, dstBase, STD140_BYTES);

        colors[dst] = colors[src];
        visible[dst] = visible[src];
        InstanceHandle moved = handles[src];
        handles[dst] = moved;
        moved.moveTo(dst);

        int srcBit = 1 << (src & 31);
        int dstBit = 1 << (dst & 31);
        if (visible[dst]) {
            atomicSetBit(visiblePageMasks, dst >> 5, dstBit);
        } else {
            atomicClearBit(visiblePageMasks, dst >> 5, dstBit);
        }
        atomicClearBit(visiblePageMasks, src >> 5, srcBit);
    }

    private void clearSlot(int index) {
        long base = slabAddr + (long) index * STD140_BYTES;
        U.setMemory(base, STD140_BYTES, (byte) 0);
        colors[index] = 0xFFFFFFFF;
        visible[index] = false;
        handles[index] = null;
        updateVisibleBit(index);
    }

    private void updateVisibleBit(int index) {
        int bit = 1 << (index & 31);
        int word = index >> 5;
        if (visible[index]) {
            atomicSetBit(visiblePageMasks, word, bit);
        } else {
            atomicClearBit(visiblePageMasks, word, bit);
        }
    }
}
//?}
