package dev.golem.core.flywheel.gl;

//? if neoforge && >=26.1 {

import com.mojang.blaze3d.opengl.GlBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

// Injected onto com.mojang.blaze3d.buffers.GpuBuffer via META-INF/interface_injection.json,
// so any GpuBuffer reference can read the underlying GL name and the persistent-map address
// without an explicit cast. GpuBuffer is abstract; the only concrete subclass in 26.1 is
// com.mojang.blaze3d.opengl.GlBuffer, whose handle / persistentBuffer fields are AT-widened
// to public for the default-method reads here. SlabBuffer is the only consumer.
public interface GpuBufferExtension {

    default int golem$glHandle() {
        return ((GlBuffer) this).handle;
    }

    default long golem$persistentAddress() {
        ByteBuffer pb = ((GlBuffer) this).persistentBuffer;
        return pb == null ? 0L : MemoryUtil.memAddress(pb);
    }
}
//?}
