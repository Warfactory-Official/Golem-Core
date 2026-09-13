package dev.golem.core.flywheel;

//? if >=26.1 {

import java.util.concurrent.atomic.AtomicInteger;

/**
 * What the last frame actually cost the GPU, in draw calls.
 *
 * <p>The whole claim instancing makes is that N copies of a machine become one draw rather than N, and
 * that claim is invisible from inside the game: a frame drawn well and a frame drawn badly look the
 * same until something counts. Draws are counted rather than derived because the two backends reach
 * the GPU differently — INSTANCING issues one {@code drawIndexed} per bucket chunk, INDIRECT one
 * {@code glMultiDrawElementsIndirect} per group covering every bucket in it — and only the call site
 * knows which it just made.
 *
 * <p>Counted into the frame in progress and rolled over at the frame boundary, so a reader always sees
 * a whole frame rather than however much of one had been issued when it looked.
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
     * <p>Separate from the draw count because they answer different questions. Draws are what the
     * frame costs to issue; this is what it costs to <em>prepare</em>, and a model that is animated
     * but standing still costs nothing here while still costing a draw. The pair is the only way to
     * see the difference between a world that is busy and one that is merely populated.
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
