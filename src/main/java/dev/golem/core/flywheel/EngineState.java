package dev.golem.core.flywheel;

//? if >=26.1 {

import dev.golem.core.GolemCore;
import org.jspecify.annotations.Nullable;

/**
 * Whether the vendored engine is drawing right now, and the one place that stops it.
 *
 * <p>The engine is an acceleration beside a vanilla renderer that already draws every machine, so no
 * failure inside it is worth a crash. Every entry point the loader calls into (level load, frame start,
 * the draw stages, a block entity arriving) runs through {@link #guard}. The first throw switches the
 * engine off for the session and logs one warning; every ownership question
 * ({@code Visuals.owns}, {@code Billboards.owns}) then answers no and the vanilla renderers take every
 * machine back on the next submit. The engine's state is torn down at the next safe point
 * ({@link #takeTeardown}), not from inside the walk that failed.
 *
 * <p>{@code /flywheel backend off} uses the same switch, as a reversible reason rather than a failure.
 */
public final class EngineState {

    /**
     * Thrown where the engine finds the device cannot do what it needs (a non-OpenGL backend, a buffer
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
     * <p>Also drops a teardown no safe point has claimed yet: left pending, the next tick's
     * {@code teardownIfPending} would tear down the manager the caller is about to build, leaving the engine
     * running and drawing nothing. A caller performs the teardown first
     * ({@code VisualizationLifecycle.teardownIfPending()}, as {@code FlywheelCommands.apply} does).
     */
    public static boolean switchOn() {
        synchronized (EngineState.class) {
            if (failed) return false;
            off = null;
            teardownPending = false;
            return true;
        }
    }

    /** The off reason {@link #followShaderPack} sets, and the only one it ever clears. */
    static final String SHADER_PACK = "a shader pack is in use";

    /**
     * Keeps the engine off for as long as Iris or Oculus has a shader pack enabled, and back on once it has not.
     *
     * <p>The published engine does the same: its backends report themselves unsupported while
     * {@code ShadersModHelper.isShaderPackInUse()}. A pack replaces the world pipeline with its own G-buffers;
     * the engine's raw GL draws land in vanilla's main target, which the pack's final pass paints over, while
     * each machine's vanilla renderer stands down for the engine, so every instanced machine is invisible.
     * Off, the vanilla renderers draw and the pack shades them.
     *
     * <p>Only the shader-pack reason is undone here; an engine the player switched off, or one that failed,
     * stays off. Call once per frame on the render thread, before anything asks whether the engine is running.
     */
    public static void followShaderPack() {
        boolean pack = dev.golem.core.client.ShaderPacks.inUse();
        if (pack && running()) {
            switchOff(SHADER_PACK);
            GolemCore.LOG.info("Golem flywheel paused while a shader pack is in use; machines draw through their "
                    + "vanilla renderers");
            dev.golem.core.flywheel.visual.VisualizationLifecycle.teardownIfPending();
        } else if (!pack && SHADER_PACK.equals(off)) {
            if (!switchOn()) return;
            dev.golem.core.flywheel.backend.BackendManager.reset();
            dev.golem.core.flywheel.visual.VisualizationLifecycle.rebuildCurrent();
            GolemCore.LOG.info("Golem flywheel resumed: no shader pack in use");
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
