package dev.golem.core.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Whether a shader pack is rendering the world: Iris (Fabric and NeoForge), or Oculus, its Forge 1.20.1 port. Both
 * publish the same stable API, {@code net.irisshaders.iris.api.v0.IrisApi}, on every node.
 *
 * <p>A shader pack replaces the world pipeline with its own framebuffers and composite passes, so effects that
 * read or write the main framebuffer during the world frame (scene copies, {@link
 * dev.golem.core.render.WorldEffects} screen-space draws) are painted over by its final pass, and entity renderers
 * run a second time in its shadow pass. {@link dev.golem.core.render.WorldEffects} asks here to draw after the pack's
 * final pass and to ignore shadow-pass submissions. Neither mod is a dependency: the API is reached through its public
 * methods, and with neither installed both answers are {@code false}. {@link #bindWorldTarget} alone reaches Iris
 * internals, as the public API has no way to bind the pack's gbuffer.
 */
public final class ShaderPacks {
    private static final Logger LOG = LoggerFactory.getLogger("Golem Core");
    private static final MethodHandle GET_INSTANCE;
    private static final MethodHandle IN_USE;
    private static final MethodHandle SHADOW_PASS;

    static {
        MethodHandle getInstance = null;
        MethodHandle inUse = null;
        MethodHandle shadowPass = null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            getInstance = lookup.findStatic(api, "getInstance", MethodType.methodType(api));
            inUse = lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class));
            shadowPass = lookup.findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError absent) {
            // neither Iris nor Oculus is installed
        }
        GET_INSTANCE = getInstance;
        IN_USE = inUse;
        SHADOW_PASS = shadowPass;
    }

    private ShaderPacks() {}

    private static boolean worldTargetResolved;
    private static MethodHandle currentPipeline;
    private static MethodHandle bindDefault;
    private static Class<?> irisPipeline;

    /** Writes native world geometry into the pack's current gbuffer, before its composite passes. Render thread. */
    public static void bindWorldTarget() {
        if (!worldTargetResolved) {
            worldTargetResolved = true;
            resolveWorldTarget();
        }
        if (bindDefault == null) return;
        try {
            Object pipeline = currentPipeline.invokeExact();
            if (irisPipeline.isInstance(pipeline)) bindDefault.invokeExact(pipeline);
        } catch (Throwable failure) {
            bindDefault = null;
            LOG.warn("Cannot bind the Iris world target; native world effects keep the current target", failure);
        }
    }

    private static void resolveWorldTarget() {
        try {
            var lookup = MethodHandles.publicLookup();
            var manager = Class.forName("net.irisshaders.iris.Iris").getMethod("getPipelineManager");
            var current = manager.getReturnType().getMethod("getPipelineNullable");
            var pipelineClass = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline");
            currentPipeline = MethodHandles.filterReturnValue(lookup.unreflect(manager), lookup.unreflect(current))
                    .asType(MethodType.methodType(Object.class));
            bindDefault = lookup.unreflect(pipelineClass.getMethod("bindDefault"))
                    .asType(MethodType.methodType(void.class, Object.class));
            irisPipeline = pipelineClass;
        } catch (ReflectiveOperationException | LinkageError | WrongMethodTypeException
                 | IllegalArgumentException failure) {
            bindDefault = null;
            LOG.warn("Iris has no world target to bind; native world effects keep the current target", failure);
        }
    }

    /**
     * True while the pack renders its shadow map: the world, entities included, drawn again from the sun's view.
     * Anything an entity renderer queues for the player's view must be skipped then.
     */
    public static boolean renderingShadowPass() {
        if (SHADOW_PASS == null) return false;
        try {
            return (boolean) SHADOW_PASS.invoke(GET_INSTANCE.invoke());
        } catch (Throwable failure) {
            return false;
        }
    }

    /** True while Iris or Oculus has a shader pack enabled. */
    public static boolean inUse() {
        if (IN_USE == null) return false;
        try {
            return (boolean) IN_USE.invoke(GET_INSTANCE.invoke());
        } catch (Throwable failure) {
            return false;
        }
    }
}
