package dev.golem.core.render;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core pipelines that draw into the level's own target during the level pass. Under a shader pack they draw into its
 * gbuffer instead, as its composite passes paint over the main target. Private-target pipelines are never added.
 */
public final class WorldPipelines {
    // RenderPipeline keeps Object's equals, so membership is by identity.
    private static final Set<RenderPipeline> PIPELINES = ConcurrentHashMap.newKeySet();

    private WorldPipelines() {}

    static RenderPipeline add(RenderPipeline pipeline) {
        PIPELINES.add(pipeline);
        return pipeline;
    }

    public static boolean contains(RenderPipeline pipeline) {
        return PIPELINES.contains(pipeline);
    }
}
//?}
