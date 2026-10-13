package dev.golem.core.flywheel.indirect;

//? if >=26.1 {

import dev.golem.core.flywheel.CrumblingDraw;
import dev.golem.core.flywheel.Mesh;
import dev.golem.core.flywheel.MeshPool;
import dev.golem.core.flywheel.instance.AbstractInstancer;
import dev.golem.core.flywheel.instance.Instance;
import dev.golem.core.flywheel.instance.InstanceInternals;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

// Per-frame scratch state for INDIRECT crumbling. Packs every CrumblingDraw across the frame
// into two growable GpuBuffers:
//
// - visListBuffer: one uvec2 per crumbling instance, contiguous (.x=0 unused, .y=instIdx).
//   Each draw's slice is at [visListBaseOffset, visListBaseOffset + instanceCount). The vertex
//   shader reads instances[visList[gl_BaseInstanceARB + gl_InstanceID].y] as in the regular
//   INDIRECT path, but the visList is built CPU-side (breaking blocks are in view, so no culling).
// - indirectBuffer: one 5-uint DrawElementsIndirectCommand per CrumblingDraw, packed in the
//   same order as the bucket iteration in IndirectDrawManager.drawCrumbling.
//
// CrumblingDraws are bucketed by (source stage-group, progress); a bucket shares one instance
// SSBO and one destroy_stage texture, so one bind of each covers its multidraw.
public final class IndirectCrumblingScratch {

    // uvec2 to match the regular indirect path's visList layout. The vertex shader reads .y as
    // the SSBO instance index. .x is unused for crumbling (no depth-sort) and written as 0.
    private static final int VIS_LIST_ENTRY_BYTES = 2 * Integer.BYTES;

    private GpuBuffer visListBuffer;
    private long visListCapacityBytes;
    private GpuBuffer indirectBuffer;
    private long indirectCapacityBytes;

    public Plan build(List<CrumblingDraw> draws) {
        if (draws.isEmpty()) return Plan.EMPTY;

        // Group by (sourceGroup, progress). sourceGroup comes from the first instance's parent
        // IndirectInstancer; visuals must keep all instances of a CrumblingDraw in one stage-group
        // (true for AssemblyMachineVisual, whose 12 meshes share the opaque stage-group). Fully
        // invisible local pages are hole-punched out of the shared object storage, so only instances
        // whose local slot still maps to a global SSBO slot are kept.
        Long2ObjectOpenHashMap<Bucket> bucketsByKey = new Long2ObjectOpenHashMap<>();
        List<Bucket> bucketOrder = new ArrayList<>();
        long totalInstances = 0L;
        int validDraws = 0;
        for (CrumblingDraw draw : draws) {
            if (draw.instances().isEmpty()) continue;
            Mesh mesh = MeshPool.mesh(draw.meshHandle());
            if (mesh.empty()) continue;
            IndirectStageGroup group = null;
            int[] globalIndices = null;
            int globalCount = 0;
            for (Instance inst : draw.instances()) {
                AbstractInstancer instancer = InstanceInternals.instancerOf(inst);
                if (!(instancer instanceof IndirectInstancer indirect)) {
                    continue;
                }
                if (group == null) {
                    group = indirect.group();
                }
                int globalIndex = indirect.tryLocal2GlobalIndex(InstanceInternals.indexOf(inst));
                if (globalIndex == ObjectStorage.INVALID_PAGE) {
                    continue;
                }
                if (globalIndices == null) {
                    globalIndices = new int[draw.instances().size()];
                }
                globalIndices[globalCount++] = globalIndex;
            }
            if (globalCount == 0) continue;
            if (group == null) continue;
            // Identity-keyed bucket: pack (System.identityHashCode(group), progress) into a
            // long. identityHashCode collisions are theoretically possible but practically
            // negligible for the small group set in flight.
            long key = ((long) System.identityHashCode(group) << 32) ^ (draw.progress() & 0xFFFFFFFFL);
            Bucket bucket = bucketsByKey.get(key);
            if (bucket == null) {
                bucket = new Bucket(group, draw.progress());
                bucketsByKey.put(key, bucket);
                bucketOrder.add(bucket);
            }
            if (globalCount != globalIndices.length) {
                int[] exact = new int[globalCount];
                System.arraycopy(globalIndices, 0, exact, 0, globalCount);
                globalIndices = exact;
            }
            bucket.draws.add(new BucketEntry(mesh, globalIndices));
            totalInstances += globalCount;
            validDraws++;
        }
        if (validDraws == 0) return Plan.EMPTY;

        long visListBytes = totalInstances * VIS_LIST_ENTRY_BYTES;
        long indirectBytes = (long) validDraws * IndirectStageGroup.INDIRECT_CMD_BYTES;
        ensureVisListCapacity(visListBytes);
        ensureIndirectCapacity(indirectBytes);

        ByteBuffer visStage = MemoryUtil.memAlloc((int) visListBytes).order(ByteOrder.nativeOrder());
        ByteBuffer indirectStage = MemoryUtil.memAlloc((int) indirectBytes).order(ByteOrder.nativeOrder());
        try {
            int visOff = 0;
            int indirectOff = 0;
            int globalDrawIdx = 0;
            for (Bucket bucket : bucketOrder) {
                bucket.firstDrawIndex = globalDrawIdx;
                for (BucketEntry entry : bucket.draws) {
                    int visListBaseOffset = visOff / VIS_LIST_ENTRY_BYTES;
                    int[] globalIndices = entry.globalIndices;
                    for (int i = 0; i < globalIndices.length; i++) {
                        // uvec2(.x = 0, .y = SSBO index). The vertex shader reads .y.
                        visStage.putInt(visOff, 0);
                        visStage.putInt(visOff + 4, globalIndices[i]);
                        visOff += VIS_LIST_ENTRY_BYTES;
                    }
                    indirectStage.putInt(indirectOff,      entry.mesh.indexCount());
                    indirectStage.putInt(indirectOff + 4,  globalIndices.length);
                    indirectStage.putInt(indirectOff + 8,  entry.mesh.firstIndex());
                    indirectStage.putInt(indirectOff + 12, entry.mesh.baseVertex());
                    indirectStage.putInt(indirectOff + 16, visListBaseOffset);
                    indirectOff += IndirectStageGroup.INDIRECT_CMD_BYTES;
                    globalDrawIdx++;
                }
                bucket.drawCount = bucket.draws.size();
            }
            visStage.position(0).limit(visOff);
            indirectStage.position(0).limit(indirectOff);
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(visListBuffer.slice(0L, visOff), visStage);
            encoder.writeToBuffer(indirectBuffer.slice(0L, indirectOff), indirectStage);
        } finally {
            MemoryUtil.memFree(visStage);
            MemoryUtil.memFree(indirectStage);
        }
        return new Plan(bucketOrder, visListBuffer, indirectBuffer);
    }

    private void ensureVisListCapacity(long requiredBytes) {
        if (visListBuffer != null && requiredBytes <= visListCapacityBytes) return;
        if (visListBuffer != null) {
            visListBuffer.close();
            visListBuffer = null;
        }
        long capacity = Math.max(requiredBytes, VIS_LIST_ENTRY_BYTES * 64L);
        visListBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Golem IndirectCrumblingScratch vis list",
                GpuBuffer.USAGE_COPY_DST,
                capacity);
        visListCapacityBytes = capacity;
    }

    private void ensureIndirectCapacity(long requiredBytes) {
        if (indirectBuffer != null && requiredBytes <= indirectCapacityBytes) return;
        if (indirectBuffer != null) {
            indirectBuffer.close();
            indirectBuffer = null;
        }
        long capacity = Math.max(requiredBytes, IndirectStageGroup.INDIRECT_CMD_BYTES * 16L);
        indirectBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Golem IndirectCrumblingScratch commands",
                GpuBuffer.USAGE_COPY_DST,
                capacity);
        indirectCapacityBytes = capacity;
    }

    public void close() {
        if (visListBuffer != null) {
            visListBuffer.close();
            visListBuffer = null;
        }
        if (indirectBuffer != null) {
            indirectBuffer.close();
            indirectBuffer = null;
        }
        visListCapacityBytes = 0L;
        indirectCapacityBytes = 0L;
    }

    public static final class Bucket {
        public final IndirectStageGroup sourceGroup;
        public final int progress;
        final List<BucketEntry> draws = new ArrayList<>();
        int firstDrawIndex;
        int drawCount;

        Bucket(IndirectStageGroup sourceGroup, int progress) {
            this.sourceGroup = sourceGroup;
            this.progress = progress;
        }

        public int firstDrawIndex() {
            return firstDrawIndex;
        }

        public int drawCount() {
            return drawCount;
        }
    }

    private record BucketEntry(Mesh mesh, int[] globalIndices) {
    }

    public record Plan(List<Bucket> buckets, GpuBuffer visListBuffer, GpuBuffer indirectBuffer) {
        static final Plan EMPTY = new Plan(List.of(), null, null);

        public boolean isEmpty() {
            return buckets.isEmpty();
        }
    }
}
//?}
