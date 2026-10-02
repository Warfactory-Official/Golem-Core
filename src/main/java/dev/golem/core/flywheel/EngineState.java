package dev.golem.core.flywheel;

//? if >=26.1 {

import dev.golem.core.GolemCore;
import org.jspecify.annotations.Nullable;

/**
 * Whether the vendored engine is drawing right now, and the one place that stops it.
 *
 * <p>The engine is an acceleration beside a vanilla renderer that already draws every machine, so no
 * failure inside it is worth a crash or a hole in the world. Every entry point the loader calls into —
 * level load, frame start, the draw stages, a block entity arriving — runs through {@link #guard}. The
 * first throw switches the engine off for the session, logs one warning, and from that moment every
 * ownership question ({@code Visuals.owns}, {@code Billboards.owns}) answers no, so the vanilla renderers
 * take every machine back on the very next submit. The engine's own state is torn down at the next safe
 * point ({@link #takeTeardown}) rather than from inside the walk that failed.
 *
 * <p>The same switch serves the player: {@code /flywheel backend off} lands here too, as a reason
 * rather than a failure, and can be reversed.
 */
public final class EngineState {

    /**
     * Thrown where the engine finds the device cannot do what it needs — a non-OpenGL backend, a buffer
     * that cannot be persistently mapped. That is a property of the machine, not of one visual, so it
     * switches the whole engine off instead of retiring visuals one at a time.
     */
    public static final class Unsupported extends IllegalStateException {
        public Unsupported(String message) {
            super(message);
        }
    }

    private static volatile @Nullable String off;
    private static volatile boolean failed;
    private static volatile boolean teardownPending;

    private EngineState() {}

    /** Whether the engine may create, update or draw anything. */
    public static boolean running() {
        return off == null;
    }

    /** Why the engine is not drawing, or null while it is. */
    public static @Nullable String offReason() {
        return off;
    }

    /** Whether the engine stopped itself on a failure; sticky for the session. */
    public static boolean failed() {
        return failed;
    }

    /**
     * Stops the engine because something inside it threw. Only the first call logs, and it logs the
     * cause, because every later one is usually the same fault seen from a different call site.
     */
    public static void fail(String where, Throwable failure) {
        synchronized (EngineState.class) {
            if (failed) return;
            failed = true;
            off = "failed during " + where;
            teardownPending = true;
        }
        GolemCore.LOG.warn("Golem flywheel failed during {} and is off for this session; every instanced "
                + "machine draws through its vanilla renderer instead", where, failure);
    }

    /** Stops the engine on request. Reversible with {@link #switchOn} unless the engine has failed. */
    public static void switchOff(String why) {
        synchronized (EngineState.class) {
            if (off == null) off = why;
            teardownPending = true;
        }
    }

    /**
     * Lets the engine run again. False, and nothing changes, once it has failed.
     *
     * <p>Also drops a teardown that no safe point has claimed yet: left pending, the next tick's
     * {@code teardownIfPending} would tear down the manager the caller is about to build, and the engine
     * would report itself running while drawing nothing. A caller therefore performs the teardown itself
     * first ({@code VisualizationLifecycle.teardownIfPending()}, as {@code FlywheelCommands.apply} does), so
     * the old managers and buffers are released before this clears the flag.
     */
    public static boolean switchOn() {
        synchronized (EngineState.class) {
            if (failed) return false;
            off = null;
            teardownPending = false;
            return true;
        }
    }

    /** Claims a pending teardown, so exactly one safe point performs it. */
    public static boolean takeTeardown() {
        synchronized (EngineState.class) {
            if (!teardownPending) return false;
            teardownPending = false;
            return true;
        }
    }

    /** Back to a fresh session. Tests only; the game never un-fails an engine. */
    static void resetForTest() {
        synchronized (EngineState.class) {
            off = null;
            failed = false;
            teardownPending = false;
        }
    }

    /**
     * Runs one engine entry point, doing nothing while the engine is off and switching it off if the
     * entry point throws. {@link LinkageError} is included because a missing access-widened member or a
     * class the device path cannot resolve is exactly the kind of fault this layer exists to absorb.
     */
    public static void guard(String where, Runnable action) {
        if (!running()) return;
        try {
            action.run();
        } catch (RuntimeException | LinkageError failure) {
            fail(where, failure);
        }
    }
}
//?}
