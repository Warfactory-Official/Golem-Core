package dev.golem.core.flywheel.instance;

//? if >=26.1 {

import dev.golem.core.flywheel.Material;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.Identifier;

// UBO-backed Instancer for the INSTANCING backend. No separate UBO: the inherited SlabBuffer
// is itself created with USAGE_UNIFORM and bound directly via pass.setUniform at draw time.
// flush() is just slabBuffer.releaseRetired() + a single glFlushMappedNamedBufferRange over
// the dirty span; setters wrote directly into the mapped slab during the visual plan.
public final class InstancedInstancer extends AbstractInstancer {

    InstancedInstancer(int meshHandle, Material material, Identifier texture) {
        super(meshHandle, material, texture);
    }

    @Override
    public void flush() {
        RenderSystem.assertOnRenderThread();
        slabBuffer().releaseRetired();
        int max = dirtyMax();
        if (max < 0) return;
        int min = dirtyMin();
        max = Math.min(max, liveCount - 1);
        if (max < min) {
            clearDirty();
            return;
        }
        long byteOffset = (long) min * STD140_BYTES;
        long byteSize = (long) (max - min + 1) * STD140_BYTES;
        slabBuffer().flushRange(byteOffset, byteSize);
        clearDirty();
    }

    public GpuBuffer gpuBuffer() {
        return slabBuffer().gpuBuffer();
    }

    @Override
    public void close() {
        liveCount = 0;
        clearDirty();
        freeSlab();
    }
}
//?}
