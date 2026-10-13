package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import dev.golem.core.GolemCore;
import dev.golem.core.flywheel.DrawStats;
import dev.golem.core.flywheel.plan.CommonPoolTaskExecutor;
import dev.golem.core.flywheel.plan.TaskExecutor;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
public final class GolemRenderSystem {

    private static final TaskExecutor EXECUTOR = createExecutor();
    private static final DistanceUpdateLimiter LIMITER = DistanceUpdateLimiter.bandedPrime();
    private static final Vec3i ORIGIN_ZERO = new Vec3i(0, 0, 0);
    private static CompletableFuture<Void> previousFramePlan = CompletableFuture.completedFuture(null);
    private static int frameId;
    private static volatile Vec3i currentRenderOrigin = ORIGIN_ZERO;

    /**
     * How often to write the engine's own census to the log, or zero for never.
     *
     * <p>An instanced frame and a vanilla frame look the same on screen, so an engine that draws nothing
     * shows no symptom. This switch makes that visible in the log:
     * {@code -Dgolem.flywheel.trace} or {@code GOLEM_FLYWHEEL_TRACE}.
     */
    private static final long TRACE_NANOS = traceInterval();
    private static long lastTrace;
    private static int peakWrites;
    private static boolean warnedIdle;
    /** The frame the engine last started running on; the idle warning counts from here, not from game start. */
    private static int runningSince;
    private static boolean wasRunning;

    private static long traceInterval() {
        String on = System.getProperty("golem.flywheel.trace");
        if (on == null || on.isEmpty()) on = System.getenv("GOLEM_FLYWHEEL_TRACE");
        return on == null || on.isEmpty() ? 0L : 5_000_000_000L;
    }

    private GolemRenderSystem() {
    }

    public static void onClientTick() {
        VisualizationLifecycle.teardownIfPending();
        LIMITER.tick();
        VisualizationManager manager = VisualizationLifecycle.current();
        if (manager != null) {
            dev.golem.core.flywheel.EngineState.guard("client tick", () -> manager.runTick(EXECUTOR));
        }
    }

    public static void awaitFrame() {
        previousFramePlan.join();
    }

    /**
     * Waits out the frame plan still in flight and forgets it, swallowing whatever it threw: the
     * teardown that calls this is already the answer to that failure.
     */
    static void settle() {
        try {
            previousFramePlan.join();
        } catch (RuntimeException ignored) {
            // Reported by whichever guard saw it first.
        }
        previousFramePlan = CompletableFuture.completedFuture(null);
        currentRenderOrigin = ORIGIN_ZERO;
    }

    public static Vec3i currentRenderOrigin() {
        return currentRenderOrigin;
    }

    public static int currentFrameId() {
        return frameId - 1;
    }

    public static void onFrameStart(float partialTick) {
        dev.golem.core.flywheel.EngineState.followShaderPack();
        VisualizationLifecycle.teardownIfPending();
        DrawStats.rollOver();
        if (!dev.golem.core.flywheel.EngineState.running()) {
            // Keep the frame counter moving: a visual that claimed the last frame must not still
            // appear to own this one if the engine comes back.
            frameId++;
            wasRunning = false;
            return;
        }
        if (!wasRunning) {
            wasRunning = true;
            runningSince = frameId;
        }
        diagnose();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        dev.golem.core.flywheel.EngineState.guard("frame start", () -> {
            previousFramePlan.join();
            VisualizationManager manager = VisualizationLifecycle.current();
            if (manager == null) {
                currentRenderOrigin = ORIGIN_ZERO;
                previousFramePlan = CompletableFuture.completedFuture(null);
                return;
            }
            var camera = minecraft.gameRenderer.getMainCamera();
            Vec3i origin = GolemFrameContext.originFor(camera);
            currentRenderOrigin = origin;
            var context = GolemFrameContext.create(camera, partialTick, frameId++, origin, LIMITER);
            manager.runFrameSynchronous(context);
            previousFramePlan = manager.runFrameAsync(EXECUTOR, context);
        });
    }

    /**
     * Reports what the engine is holding, and complains once if it is holding models it never draws.
     *
     * <p>Visuals with no instances is the one failure this engine cannot show you: every machine still
     * appears, correctly, through the renderer the instanced path was meant to relieve.
     */
    private static void diagnose() {
        int[] census = GemBlockEntityVisual.census();
        long now = System.nanoTime();

        // Track the peak, not just the last frame: a full rewrite (render origin moving, a light update)
        // lands on one frame out of hundreds between two trace lines.
        int frameWrites = DrawStats.writes();
        if (frameWrites > peakWrites) peakWrites = frameWrites;
        if (TRACE_NANOS > 0 && now - lastTrace >= TRACE_NANOS) {
            lastTrace = now;
            GolemCore.LOG.info(
                    "Golem flywheel: {} gem visuals ({} healthy, {} animated, {} moving), {} instances, "
                            + "{} drew last frame, {} draw calls, {} transforms written (peak {}), "
                            + "{} parts skipped",
                    census[0], census[1], census[4], census[5], census[2], census[3], DrawStats.draws(),
                    frameWrites, peakWrites, DrawStats.skipped());
            peakWrites = 0;
        }
        if (!warnedIdle && census[0] > 0 && !GemBlockEntityVisual.announced() && frameId - runningSince > 120) {
            warnedIdle = true;
            GolemCore.LOG.warn(
                    "Golem flywheel holds {} gem visuals but has drawn none in {} frames; those machines "
                            + "are rendering through their vanilla renderers",
                    census[0], frameId - runningSince);
        }
    }

    private static TaskExecutor createExecutor() {
        return CommonPoolTaskExecutor.INSTANCE;
    }
}
//?}
