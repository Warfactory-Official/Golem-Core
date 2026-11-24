package dev.golem.core.render;

//? if >=1.21.11 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.golem.core.GolemCore;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core pipelines that draw into the level's own target during the level pass. Under a shader pack they draw into its
 * gbuffer instead, as its composite passes paint over the main target. Private-target pipelines are never added.
 * One added with the vanilla pipeline it imitates draws with that pipeline's pack programs; others keep their shaders.
 */
public final class WorldPipelines {
    // RenderPipeline keeps Object's equals, so membership is by identity.
    private static final Set<RenderPipeline> PIPELINES = ConcurrentHashMap.newKeySet();
    // Iris's own vanilla table, so a pipeline gets the context-dependent program (hand, block entity) vanilla gets.
    // GOLEM_IRIS_MAP=off (or -Dgolem.iris.map=off) keeps Core's own shaders under a pack, to compare.
    private static final MethodHandle COPY_PIPELINE = "off".equalsIgnoreCase(System.getProperty("golem.iris.map",
            System.getenv().getOrDefault("GOLEM_IRIS_MAP", "on"))) ? null : copyPipeline();

    private WorldPipelines() {}

    static RenderPipeline add(RenderPipeline pipeline) {
        PIPELINES.add(pipeline);
        return pipeline;
    }

    static RenderPipeline add(RenderPipeline pipeline, RenderPipeline drawnLike) {
        add(pipeline);
        if (COPY_PIPELINE != null) {
            try {
                COPY_PIPELINE.invokeExact(drawnLike, pipeline);
            } catch (Throwable failure) {
                GolemCore.LOG.warn("Iris did not take {}'s pack programs for {}", drawnLike.getLocation(),
                        pipeline.getLocation(), failure);
            }
        }
        return pipeline;
    }

    public static boolean contains(RenderPipeline pipeline) {
        return PIPELINES.contains(pipeline);
    }

    private static MethodHandle copyPipeline() {
        try {
            Class<?> pipelines = Class.forName("net.irisshaders.iris.pipeline.IrisPipelines");
            return MethodHandles.publicLookup().findStatic(pipelines, "copyPipeline",
                    MethodType.methodType(void.class, RenderPipeline.class, RenderPipeline.class));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException | LinkageError changed) {
            GolemCore.LOG.warn("Iris has no copyPipeline; Core's world pipelines keep their own shaders under a pack",
                    changed);
            return null;
        }
    }
}
//?}
