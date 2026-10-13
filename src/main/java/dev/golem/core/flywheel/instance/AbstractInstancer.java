package dev.golem.core.flywheel.instance;

//? if >=26.1 {

import dev.golem.core.flywheel.Instancer;
import dev.golem.core.flywheel.Material;
import dev.golem.core.flywheel.gl.SlabBuffer;
import dev.golem.core.flywheel.visual.DynamicVisual;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;

// Per-bucket persistent CPU staging shared by both backends. Subclasses (InstancedInstancer
// for UBO, IndirectInstancer for SSBO) own the GPU-side specifics: where the bytes go on
// flush, how growth is handled, when the GPU buffer gets created. Visuals only see the
// Instancer interface. The public allocate() returns an Instance handle.
//
// std140 layout per instance (112 bytes):
//   ivec2 OverlayCoords  (offset 0)
//   ivec2 LightCoords    (offset 8)
//   vec4  Color          (offset 16)
//   mat4  ModelMat       (offset 32, ends at 96)
//   vec4  UVTransform    (offset 96, ends at 112): (offsetU, offsetV, scaleU, scaleV)
//
// UVTransform applies in the vertex shader as `texCoord0 = UV0 * scale + offset`; identity is
// (0, 0, 1, 1). Sprite-strip animations (rad fog frame cycle, RBMK fire/steam/mush, MukeCloud)
// use it to pick one sub-rect of an N×M frame sheet.
//
// Storage: one persistent-mapped GL buffer of `capacity * 112` bytes in std140 layout is the
// source of truth. Setters write absolute positions into the mapped ByteBuffer; the indirect
// backend uploads dirty pages with one glFlushMappedNamedBufferRange + glCopyNamedBufferSubData
// into the shared object SSBO (no staging ring, no scatter compute). SlabBuffer creates the
// slab in the constructor (render thread); close() frees it; ensureCapacity reallocates via
// SlabBuffer's retired-frame pattern and zeros the new tail. Two heap-side caches exist
// because they depend on each other:
//   - colors[] lets setVisible(true) restore the prior color; the slab's color slot is zeroed
//     while invisible (the GPU cull pass reads an all-zero color as the dead-instance sentinel).
//   - visible[] tells setColor whether to overwrite the slab's color slot (only when visible)
//     or just update the cache and leave the sentinel.
//
// Threading: the per-slot writers (writeTransform / writeLight / writeOverlay / writeColor /
// writeVisible / writeUV) are lock-free because each Instance handle owns a unique slot, so
// workers touch disjoint slab regions and cache slots. Two pieces of state are shared:
//   - The dirty range is sharded across SHARD_COUNT cache-line-padded (min, max) cells. Each
//     markDirty hashes the calling thread to a shard; dirtyMin/Max reduce all shards on the
//     render thread at flush time.
//   - visiblePageMasks packs 32 slots into one int word, so bit set/clear go through
//     VarHandle.compareAndSet (as upstream Flywheel's per-page AtomicInteger validity bitset,
//     without the AtomicIntegerArray indirection).
// The structural methods (allocate, free, ensureCapacity, flush, writeStd140, close) run
// outside visual writer tasks. Backend parallelUpdate may reshuffle slots only after the
// visual frame plans have completed.
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

    // Native std140 slab. slabBuffer is a persistent-mapped GL buffer; slab is refreshed on grow.
    private final SlabBuffer slabBuffer;
    protected ByteBuffer slab;
    private final byte[] swapScratch = new byte[STD140_BYTES];
    private final byte[] swapOther = new byte[STD140_BYTES];
    protected int capacity;

    // Side caches: only the two fields whose semantics depend on each other (see class doc).
    protected int[] colors;
    protected boolean[] visible;
    // Packed 32-slot visibility masks for backends that batch work per fixed-size page.
    // Mirrors upstream Flywheel's per-page AtomicInteger validity bitset: writeVisible runs
    // on workers in the parallel frame phase, so two workers updating different slots in the
    // same 32-slot word race the read-modify-write. Mutated via VarHandle.compareAndSet to
    // avoid AtomicIntegerArray's pointer indirection.
    private int[] visiblePageMasks;
    private static final VarHandle INT_ARRAY = MethodHandles.arrayElementVarHandle(int[].class);
    private static final VarHandle LONG_ARRAY = MethodHandles.arrayElementVarHandle(long[].class);

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
        this.slab = slabBuffer.mapped();
        // glNamedBufferStorage doesn't zero; CPU-zero the mapped region so unwritten slots
        // hold the dead-instance sentinel (all-zero color etc.).
        zero(0, (int) initialBytes);
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
        int base = index * STD140_BYTES;
        // Reset to defaults: zero everything, then write the non-zero values. setMemory of
        // 112 bytes is one intrinsic memset; the targeted putFloats afterwards are the
        // diagonal of mat4, the white color, and the identity UV scale.
        zero(base, STD140_BYTES);
        slab.putFloat(base + OFF_COLOR,      1F);
        slab.putFloat(base + OFF_COLOR + 4,  1F);
        slab.putFloat(base + OFF_COLOR + 8,  1F);
        slab.putFloat(base + OFF_COLOR + 12, 1F);
        slab.putFloat(base + OFF_MAT,        1F);
        slab.putFloat(base + OFF_MAT + 20,   1F);
        slab.putFloat(base + OFF_MAT + 40,   1F);
        slab.putFloat(base + OFF_MAT + 60,   1F);
        slab.putFloat(base + OFF_UV + 8,     1F);
        slab.putFloat(base + OFF_UV + 12,    1F);
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
        m.get(index * STD140_BYTES + OFF_MAT, slab);
        markDirty(index);
    }

    void writeLight(int index, int light) {
        // Pack the two uint16 channels (block, sky) into the std140 ivec2: an int per
        // 4 bytes. Bottom 16 bits at +0, top 16 at +4 of the ivec2's 8-byte slot.
        int base = index * STD140_BYTES + OFF_LIGHT;
        slab.putInt(base,     light & 0xFFFF);
        slab.putInt(base + 4, light >>> 16 & 0xFFFF);
        markDirty(index);
    }

    void writeOverlay(int index, int overlay) {
        int base = index * STD140_BYTES + OFF_OVERLAY;
        slab.putInt(base,     overlay & 0xFFFF);
        slab.putInt(base + 4, overlay >>> 16 & 0xFFFF);
        markDirty(index);
    }

    void writeColor(int index, int color) {
        colors[index] = color;
        // Only blast over the slab color when visible. Otherwise the dead-instance
        // sentinel (all-zero color) must remain in place.
        if (visible[index]) {
            int base = index * STD140_BYTES + OFF_COLOR;
            slab.putFloat(base,      ARGB.redFloat(color));
            slab.putFloat(base + 4,  ARGB.greenFloat(color));
            slab.putFloat(base + 8,  ARGB.blueFloat(color));
            slab.putFloat(base + 12, ARGB.alphaFloat(color));
        }
        markDirty(index);
    }

    // Raw-float writer for materials that hijack InstanceColor as per-instance numerical data
    // (slide offsets for CUTOUT_CLIP_SLAB / CUTOUT_CLIP_HALFSPACE, etc.). Bypasses the ARGB-to-byte-to-float round-trip
    // so the values land in the slab with full float precision. Skips the colors[] cache.
    // setVisible(true) would restore the cached int form (lossy), so callers using rawColor
    // must re-write each frame or avoid setVisible toggles. Visibility check is the same as
    // writeColor: silently ignored on invisible slots so the dead-instance sentinel stays.
    void writeRawColor(int index, float r, float g, float b, float a) {
        if (!visible[index]) return;
        int base = index * STD140_BYTES + OFF_COLOR;
        slab.putFloat(base,      r);
        slab.putFloat(base + 4,  g);
        slab.putFloat(base + 8,  b);
        slab.putFloat(base + 12, a);
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
        int base = index * STD140_BYTES + OFF_COLOR;
        if (v) {
            int color = colors[index];
            slab.putFloat(base,      ARGB.redFloat(color));
            slab.putFloat(base + 4,  ARGB.greenFloat(color));
            slab.putFloat(base + 8,  ARGB.blueFloat(color));
            slab.putFloat(base + 12, ARGB.alphaFloat(color));
        } else {
            slab.putLong(base,     0L);
            slab.putLong(base + 8, 0L);
        }
        markDirty(index);
    }

    void writeUV(int index, float offsetU, float offsetV, float scaleU, float scaleV) {
        int base = index * STD140_BYTES + OFF_UV;
        slab.putFloat(base,      offsetU);
        slab.putFloat(base + 4,  offsetV);
        slab.putFloat(base + 8,  scaleU);
        slab.putFloat(base + 12, scaleV);
        markDirty(index);
    }

    // Bulk-copy one slot's std140 record into dst at dstOffset. Used by CrumblingScratch,
    // which stitches instances from multiple instancers (different slabs) into one transient
    // UBO. The mapped slab remains the source, so there is no per-instance repacking.
    public void writeStd140(int index, ByteBuffer dst, int dstOffset) {
        ByteBuffer source = slab.duplicate().order(ByteOrder.nativeOrder());
        source.position(index * STD140_BYTES).limit((index + 1) * STD140_BYTES);
        ByteBuffer target = dst.duplicate().order(ByteOrder.nativeOrder());
        target.position(dstOffset);
        target.put(source);
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

    // Lock-free CAS loop. Parallel frame workers mark slots dirty; each call lowers its shard's
    // min to `index` if smaller and raises its max if larger. The shard is picked by hashing the
    // calling thread. getLongOpaque inside the loop is sufficient: compareAndSetLong
    // re-validates the value and carries volatile (release+acquire) semantics on success.
    protected final void markDirty(int index) {
        int base = (Thread.currentThread().hashCode() & SHARD_MASK) * SHARD_STRIDE;
        int minIndex = base + MIN_OFF_IN_SHARD;
        int maxIndex = base + MAX_OFF_IN_SHARD;
        long v = index;
        long cur;
        do {
            cur = (long) LONG_ARRAY.getOpaque(dirtyShards, minIndex);
            if (v >= cur) break;
        } while (!LONG_ARRAY.compareAndSet(dirtyShards, minIndex, cur, v));
        do {
            cur = (long) LONG_ARRAY.getOpaque(dirtyShards, maxIndex);
            if (v <= cur) break;
        } while (!LONG_ARRAY.compareAndSet(dirtyShards, maxIndex, cur, v));
    }

    // Render-thread reads: reduce all shards into a global (min, max). Acquire ensures we
    // observe every per-slot store published by the workers before they entered the
    // dirty-range CAS. (In practice the frame future's join already gives us this
    // happens-before; getLongAcquire just makes it explicit and free.) O(SHARD_COUNT)
    // acquire-loads at flush time, roughly 16 ns total.
    public final int dirtyMin() {
        long min = Integer.MAX_VALUE;
        for (int s = 0; s < SHARD_COUNT; s++) {
            long v = (long) LONG_ARRAY.getAcquire(dirtyShards, s * SHARD_STRIDE + MIN_OFF_IN_SHARD);
            if (v < min) min = v;
        }
        return (int) min;
    }

    public final int dirtyMax() {
        long max = -1L;
        for (int s = 0; s < SHARD_COUNT; s++) {
            long v = (long) LONG_ARRAY.getAcquire(dirtyShards, s * SHARD_STRIDE + MAX_OFF_IN_SHARD);
            if (v > max) max = v;
        }
        return (int) max;
    }

    // Force the entire live range to be dirty. Subclasses call this after growing GPU
    // capacity, since the new buffer needs every slot re-uploaded. Render-thread only:
    // writes the full range into shard 0; the dirtyMin/Max reduce will pick it up.
    protected final void markFullyDirty() {
        if (liveCount > 0) {
            LONG_ARRAY.setRelease(dirtyShards, MIN_OFF_IN_SHARD, 0L);
            LONG_ARRAY.setRelease(dirtyShards, MAX_OFF_IN_SHARD, (long) (liveCount - 1));
        }
    }

    protected final void clearDirty() {
        for (int s = 0; s < SHARD_COUNT; s++) {
            LONG_ARRAY.setRelease(dirtyShards, s * SHARD_STRIDE + MIN_OFF_IN_SHARD, (long) Integer.MAX_VALUE);
            LONG_ARRAY.setRelease(dirtyShards, s * SHARD_STRIDE + MAX_OFF_IN_SHARD, -1L);
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
        return (int) INT_ARRAY.getAcquire(visiblePageMasks, page);
    }

    private static void atomicSetBit(int[] arr, int word, int bit) {
        int cur;
        do {
            cur = (int) INT_ARRAY.getOpaque(arr, word);
            if ((cur & bit) != 0) return;
        } while (!INT_ARRAY.compareAndSet(arr, word, cur, cur | bit));
    }

    private static void atomicClearBit(int[] arr, int word, int bit) {
        int cur;
        do {
            cur = (int) INT_ARRAY.getOpaque(arr, word);
            if ((cur & bit) == 0) return;
        } while (!INT_ARRAY.compareAndSet(arr, word, cur, cur & ~bit));
    }

    // Protected so backend-specific instancers can react when CPU capacity grows (e.g. stage-
    // local page-mapping growth on the INDIRECT path). Render-thread only: slabBuffer.grow
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
            slab = slabBuffer.mapped();
            // The new GPU handle has no contents. slabBuffer's grow CPU-memcpys old-mapped
            // into new-mapped for CPU continuity, but the destination object buffer the
            // indirect backend copies into doesn't yet have a glCopyNamedBufferSubData from
            // the new handle. Re-flag the live range so the next upload pushes everything.
            markFullyDirty();
        }
        // Zero the tail so unallocated slots stay zero (dead-instance sentinel). Always
        // runs. Even if the buffer didn't physically grow, we still expanded the slot range.
        zero((int) oldSize, (int) (newSize - oldSize));
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
        slab = null;
    }

    protected final void swapSlots(int a, int b) {
        if (a == b) return;

        int aBase = a * STD140_BYTES;
        int bBase = b * STD140_BYTES;
        ByteBuffer view = slab.duplicate().order(ByteOrder.nativeOrder());
        view.get(aBase, swapScratch);
        view.get(bBase, swapOther);
        view.put(aBase, swapOther);
        view.put(bBase, swapScratch);

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
        int srcBase = src * STD140_BYTES;
        int dstBase = dst * STD140_BYTES;
        ByteBuffer source = slab.duplicate().order(ByteOrder.nativeOrder());
        source.position(srcBase).limit(srcBase + STD140_BYTES);
        ByteBuffer target = slab.duplicate().order(ByteOrder.nativeOrder());
        target.position(dstBase);
        target.put(source);

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
        int base = index * STD140_BYTES;
        zero(base, STD140_BYTES);
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

    private void zero(int offset, int length) {
        for (int i = offset, end = offset + length; i < end; i++) slab.put(i, (byte) 0);
    }
}
//?}
