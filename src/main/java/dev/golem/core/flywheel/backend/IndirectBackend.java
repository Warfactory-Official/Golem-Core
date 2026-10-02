package dev.golem.core.flywheel.backend;

//? if >=26.1 {

import dev.golem.core.GolemCore;
import dev.golem.core.flywheel.DrawManager;
import dev.golem.core.flywheel.indirect.IndirectDrawManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

// Higher-priority backend (1000) that uses SSBO storage + glMultiDrawElementsIndirect + a
// compute culling pass. supported() is the real GL feature check plus the slab requirement shared
// with INSTANCING (priority 500), which is the fallback when either half is missing.
public final class IndirectBackend implements Backend {

    public static final IndirectBackend INSTANCE = new IndirectBackend();

    private Boolean cachedCapsPresent;

    private IndirectBackend() {
    }

    @Override
    public String name() {
        return "indirect";
    }

    @Override
    public int priority() {
        return 1000;
    }

    // Both halves: its own GL features, and the persistently mapped slab every backend here allocates
    // instance rows in (Backends.slabsSupported).
    @Override
    public boolean supported() {
        return capabilitiesPresent() && Backends.slabsSupported();
    }

    // GL feature check matching upstream Flywheel's GlCompat.SUPPORTS_INDIRECT (cull shader,
    // DSA, multi-draw indirect, gl_BaseInstance, SSBO, multi-bind, std140 layout qualifier).
    // Mojang creates a GL 3.3 core context (see com.mojang.blaze3d.opengl.GlBackend.setWindowHints),
    // so we can't gate on OpenGL43/46 — those flags are always false here. The ARB extensions are
    // still exposed by drivers in a 3.3 context if the hardware supports them, which is the
    // load-bearing assumption.
    //
    // Cached because GLCapabilities is invariant for the lifetime of the GL context. The
    // missing-extension log fires once on first check so we can diagnose support failures
    // without flooding the log.
    public boolean capabilitiesPresent() {
        Boolean cached = cachedCapsPresent;
        if (cached != null) return cached;
        GLCapabilities caps = GL.getCapabilities();
        StringBuilder missing = null;
        missing = check(missing, "GL_ARB_compute_shader", caps.GL_ARB_compute_shader);
        missing = check(missing, "GL_ARB_direct_state_access", caps.GL_ARB_direct_state_access);
        missing = check(missing, "GL_ARB_multi_bind", caps.GL_ARB_multi_bind);
        missing = check(missing, "GL_ARB_multi_draw_indirect", caps.GL_ARB_multi_draw_indirect);
        missing = check(missing, "GL_ARB_shader_draw_parameters", caps.GL_ARB_shader_draw_parameters);
        missing = check(missing, "GL_ARB_shader_storage_buffer_object", caps.GL_ARB_shader_storage_buffer_object);
        missing = check(missing, "GL_ARB_shading_language_420pack", caps.GL_ARB_shading_language_420pack);
        boolean ok = missing == null;
        if (!ok) {
            GolemCore.LOG.info("Golem INDIRECT backend disabled — missing GL extensions: {}", missing);
        }
        cachedCapsPresent = ok;
        return ok;
    }

    private static StringBuilder check(StringBuilder acc, String name, boolean present) {
        if (present) return acc;
        if (acc == null) acc = new StringBuilder(name);
        else acc.append(", ").append(name);
        return acc;
    }

    @Override
    public DrawManager createDrawManager() {
        return new IndirectDrawManager();
    }
}
//?}
