package dev.golem.core.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Whether a shader pack is rendering the world: Iris (Fabric and NeoForge), or Oculus, its Forge 1.20.1 port. Both
 * publish the same stable API, {@code net.irisshaders.iris.api.v0.IrisApi}, on every node.
 *
 * <p>A shader pack replaces the world pipeline with its own framebuffers and composite passes, so effects that
 * read or write the main framebuffer during the world frame (scene copies, {@link
 * dev.golem.core.render.WorldEffects} screen-space draws) are painted over by its final pass, and entity renderers
 * run a second time in its shadow pass. {@link dev.golem.core.render.WorldEffects} asks here to draw after the pack's
 * final pass and to ignore shadow-pass submissions. Neither mod is a dependency: the API is reached through its public
 * methods only, and with neither installed both answers are {@code false}.
 */
public final class ShaderPacks {
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
