package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

import dev.golem.core.flywheel.plan.CommonPoolTaskExecutor;
import dev.golem.core.flywheel.plan.TaskExecutor;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
public final class GolemRenderSystem {

    private static final TaskExecutor EXECUTOR = createExecutor();
    private static final DistanceUpdateLimiter LIMITER = DistanceUpdateLimiter.bandedPrime();
    private static final Vec3i ORIGIN_ZERO = new Vec3i(0, 0, 0);
    private static CompletableFuture<Void> previousFramePlan = CompletableFuture.completedFuture(null);
    private static int frameId;
    private static volatile Vec3i currentRenderOrigin = ORIGIN_ZERO;

    private GolemRenderSystem() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        LIMITER.tick();
        VisualizationManager manager = VisualizationLifecycle.current();
        if (manager != null) {
            manager.runTick(EXECUTOR);
        }
    }

    public static void awaitFrame() {
        previousFramePlan.join();
    }

    public static Vec3i currentRenderOrigin() {
        return currentRenderOrigin;
    }

    public static int currentFrameId() {
        return frameId - 1;
    }

    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        previousFramePlan.join();
        VisualizationManager manager = VisualizationLifecycle.current();
        if (manager == null) {
            currentRenderOrigin = ORIGIN_ZERO;
            previousFramePlan = CompletableFuture.completedFuture(null);
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = minecraft.gameRenderer.getMainCamera();
        Vec3i origin = GolemFrameContext.originFor(camera);
        currentRenderOrigin = origin;
        var context = GolemFrameContext.create(camera, partialTick, frameId++, origin, LIMITER);
        previousFramePlan = manager.runFrameAsync(EXECUTOR, context);
    }

    private static TaskExecutor createExecutor() {
        return CommonPoolTaskExecutor.INSTANCE;
    }
}
//?}
