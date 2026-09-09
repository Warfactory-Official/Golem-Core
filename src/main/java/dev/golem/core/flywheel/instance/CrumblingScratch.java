package dev.golem.core.flywheel.instance;

//? if neoforge && >=26.1 {

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.system.MemoryUtil;

// Transient UBO for the crumbling pass. The main per-Instancer UBOs can't be sliced per instance
// without UBO offset alignment problems (typically 256 B; our stride is 96 B). Instead, the
// crumbling pass packs each (mesh, progress) group of instances into a small dedicated UBO and
// binds the whole thing — gl_InstanceID indexes into the packed range starting at 0.
//
// fill() must be called BEFORE opening a render pass — it issues writeToBuffer which is
// forbidden inside an open pass. Returns a parallel list of GpuBufferSlices, one per input
// group, so the caller can bind them inside the pass.
public final class CrumblingScratch {

    // GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT is driver-dependent; the conservative value is 256
    // (NVIDIA, Intel, and Mojang's own validator all enforce this). Each group's slice offset
    // must be a multiple of this; group lengths do not need to be aligned.
    private static final int UBO_OFFSET_ALIGNMENT = 256;
    private static final int MIN_CAPACITY_BYTES = MIN_CAP_BYTES();

    private static int MIN_CAP_BYTES() {
        return Math.max(UBO_OFFSET_ALIGNMENT, 16 * AbstractInstancer.STD140_BYTES);
    }

    private GpuBuffer gpuBuffer;
    private long gpuCapacityBytes;

    public synchronized List<GpuBufferSlice> fill(List<List<Instance>> groups) {
        RenderSystem.assertOnRenderThread();
        if (groups.isEmpty()) return List.of();

        // Compute per-group aligned offsets up front so we know how big the buffer needs to be.
        long[] offsets = new long[groups.size()];
        long cursor = 0L;
        boolean any = false;
        for (int g = 0; g < groups.size(); g++) {
            int count = groups.get(g).size();
            if (count == 0) {
                offsets[g] = cursor;
                continue;
            }
            cursor = alignUp(cursor, UBO_OFFSET_ALIGNMENT);
            offsets[g] = cursor;
            cursor += (long) count * AbstractInstancer.STD140_BYTES;
            any = true;
        }
        if (!any) return List.of();
        long totalBytes = cursor;
        ensureCapacity(totalBytes);

        // memCalloc zeros the buffer — alignment gaps between groups stay zero and never confuse
        // a validator (no GPU consumer reads them, but writeToBuffer uploads the whole range).
        ByteBuffer staging = MemoryUtil.memCalloc((int) totalBytes).order(ByteOrder.nativeOrder());
        try {
            for (int g = 0; g < groups.size(); g++) {
                List<Instance> group = groups.get(g);
                int count = group.size();
                if (count == 0) continue;
                int writeOff = (int) offsets[g];
                for (int j = 0; j < count; j++) {
                    InstanceHandle handle = (InstanceHandle) group.get(j);
                    handle.instancer().writeStd140(handle.index(), staging, writeOff);
                    writeOff += AbstractInstancer.STD140_BYTES;
                }
            }
            staging.position(0).limit((int) totalBytes);
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(gpuBuffer.slice(0L, totalBytes), staging);
        } finally {
            MemoryUtil.memFree(staging);
        }

        List<GpuBufferSlice> slices = new ArrayList<>(groups.size());
        for (int g = 0; g < groups.size(); g++) {
            int count = groups.get(g).size();
            if (count == 0) {
                slices.add(null);
                continue;
            }
            long len = (long) count * AbstractInstancer.STD140_BYTES;
            slices.add(gpuBuffer.slice(offsets[g], len));
        }
        return slices;
    }

    private static long alignUp(long value, int alignment) {
        long mask = alignment - 1L;
        return (value + mask) & ~mask;
    }

    private void ensureCapacity(long requiredBytes) {
        if (requiredBytes <= gpuCapacityBytes && gpuBuffer != null) return;
        long targetCap = gpuCapacityBytes == 0 ? MIN_CAPACITY_BYTES : gpuCapacityBytes;
        while (targetCap < requiredBytes) targetCap <<= 1;
        if (gpuBuffer != null) {
            gpuBuffer.close();
            gpuBuffer = null;
        }
        gpuBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Golem CrumblingScratch",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                targetCap);
        gpuCapacityBytes = targetCap;
    }

    public synchronized void close() {
        if (gpuBuffer != null) {
            gpuBuffer.close();
            gpuBuffer = null;
        }
        gpuCapacityBytes = 0L;
    }
}
//?}
