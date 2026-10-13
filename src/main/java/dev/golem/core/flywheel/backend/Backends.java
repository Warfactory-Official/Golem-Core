package dev.golem.core.flywheel.backend;

//? if >=26.1 {

import dev.golem.core.GolemCore;
import dev.golem.core.flywheel.DrawManager;
import dev.golem.core.flywheel.instance.InstancedDrawManager;
import java.util.List;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

// Mirrors upstream Flywheel's Backends. Capability-gated INDIRECT has priority 1000, with INSTANCING at
// priority 500 as its fallback. BATCHING was removed upstream in Dec 2023.
public final class Backends {

    public static final Backend INSTANCING = new Backend() {
        @Override
        public String name() {
            return "instancing";
        }

        @Override
        public int priority() {
            return 500;
        }

        @Override
        public boolean supported() {
            return slabsSupported();
        }

        @Override
        public DrawManager createDrawManager() {
            return new InstancedDrawManager();
        }
    };

    public static final IndirectBackend INDIRECT = IndirectBackend.INSTANCE;

    private static Boolean slabs;

    private Backends() {
    }

    public static void init() {
        BackendManager.register(INSTANCING);
        BackendManager.register(INDIRECT);
    }

    /**
     * What every backend here needs from the device, because both allocate their instance rows in a
     * {@code SlabBuffer}: vanilla's render device must be the OpenGL one (the slab reads the
     * access-widened {@code GlBuffer} handle and mapping), vanilla must have chosen immutable buffer
     * storage (only that path maps a buffer persistently; the mutable fallback leaves
     * {@code persistentBuffer} null), and the driver must expose {@code glFlushMappedNamedBufferRange},
     * which the slab calls directly for its explicit flushes. Upstream's backend needs only GL 3.3; the slab is
     * this port's addition. Cached for the life of the window.
     */
    public static synchronized boolean slabsSupported() {
        Boolean cached = slabs;
        if (cached != null) return cached;
        String missing = missingSlabRequirements();
        boolean ok = missing == null;
        if (!ok) GolemCore.LOG.warn("Golem flywheel backends disabled: {}", missing);
        slabs = ok;
        return ok;
    }

    private static String missingSlabRequirements() {
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        String backend = device.getBackendName();
        if (!"OpenGL".equals(backend)) return "render device is " + backend + ", not OpenGL";
        List<String> enabled = device.getEnabledExtensions();
        if (!enabled.contains("GL_ARB_buffer_storage")) {
            return "vanilla is not using GL_ARB_buffer_storage, so no buffer can be persistently mapped";
        }
        GLCapabilities caps = GL.getCapabilities();
        if (caps.glFlushMappedNamedBufferRange == 0L) {
            return "driver lacks glFlushMappedNamedBufferRange (GL 4.5 / GL_ARB_direct_state_access)";
        }
        return null;
    }
}
//?}
