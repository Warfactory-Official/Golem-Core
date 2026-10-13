package dev.golem.core.flywheel;

//? if >=26.1 {

import java.util.concurrent.atomic.AtomicInteger;

/**
 * What the last frame actually cost the GPU, in draw calls.
 *
 * <p>Instancing turns N copies of a machine into one draw, which is invisible from inside the game until
 * something counts. Draws are counted at the call site because the backends differ: INSTANCING issues one
 * {@code drawIndexed} per bucket chunk, INDIRECT one {@code glMultiDrawElementsIndirect} per group.
 *
 * <p>Counted into the frame in progress and rolled over at the frame boundary, so a reader always sees a
 * whole frame.
 */
public final class DrawStats {

    private static final AtomicInteger DRAWS = new AtomicInteger();
    private static final AtomicInteger BUCKETS = new AtomicInteger();
    private static final AtomicInteger WRITES = new AtomicInteger();
    private static final AtomicInteger SKIPPED = new AtomicInteger();
    private static volatile int lastDraws;
    private static volatile int lastBuckets;
    private static volatile int lastWrites;
    private static volatile int lastSkipped;

    private DrawStats() {
    }

    /** One GPU draw call covering {@code buckets} instancer buckets. */
    public static void draw(int buckets) {
        DRAWS.incrementAndGet();
        BUCKETS.addAndGet(buckets);
    }

    /**
     * One instance transform actually sent to the GPU.
     *
     * <p>Separate from the draw count: draws are what the frame costs to issue, this is what it costs to
     * <em>prepare</em>. A standing-still animated model costs a draw but nothing here.
     */
    public static void wrote() {
        WRITES.incrementAndGet();
    }

    /** One part the walk proved unchanged and stepped over without visiting. */
    public static void skipped(int parts) {
        SKIPPED.addAndGet(parts);
    }

    /** Closes the frame being counted and starts the next. */
    public static void rollOver() {
        lastDraws = DRAWS.getAndSet(0);
        lastBuckets = BUCKETS.getAndSet(0);
        lastWrites = WRITES.getAndSet(0);
        lastSkipped = SKIPPED.getAndSet(0);
    }

    /** GPU draw calls issued during the last complete frame. */
    public static int draws() {
        return lastDraws;
    }

    /** Instancer buckets those draws covered — equal to the draws on a backend that cannot batch. */
    public static int buckets() {
        return lastBuckets;
    }

    /** Instance transforms written during the last complete frame. */
    public static int writes() {
        return lastWrites;
    }

    /** Parts the walk stepped over during the last complete frame without recomputing them. */
    public static int skipped() {
        return lastSkipped;
    }
}
//?}
