package dev.golem.core.flywheel.indirect;

//? if >=26.1 {

import dev.golem.core.flywheel.Material;
import dev.golem.core.flywheel.Mesh;
import dev.golem.core.flywheel.MeshPool;
import dev.golem.core.flywheel.instance.AbstractInstancer;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

// Stage-local indirect culling group. All instancers in the same Material.Stage share:
// - one ObjectStorage (objectBuffer at SSBO binding 0, frameDescriptorBuffer at binding 6)
// - one model meta buffer (binding 2), one indirect command buffer (binding 3), one vis list
//   buffer (binding 1), and (translucent only) a scratch vis list (binding 5) + radix
//   histogram (binding 4)
//
// The async frame plan prepares the active instancer view and submission spans; render-thread
// upload only routes dirty pages, descriptors, model meta, and indirect commands through the
// shared StagingBuffer.
public final class IndirectStageGroup {

    // visList = uvec2 (depthKey, globalInstanceIndex). Cull writes; sort/draw read.
    public static final int VIS_LIST_ENTRY_BYTES = 2 * Integer.BYTES;
    // DrawElementsIndirectCommand: count, instanceCount, firstIndex, baseVertex, baseInstance.
    public static final int INDIRECT_CMD_BYTES = 5 * Integer.BYTES;
    // std430 ModelMeta: instanceCount, visListBaseOffset, instanceMaxCount, flags, vec4 boundingSphere.
    public static final int MODEL_META_BYTES = 32;

    private static final Comparator<IndirectInstancer> DRAW_ORDER = Comparator
            .comparingInt((IndirectInstancer i) -> i.material().ordinal())
            .thenComparing(i -> i.texture().getNamespace())
            .thenComparing(i -> i.texture().getPath())
            .thenComparingInt(IndirectInstancer::meshHandle);

    private final Material.Stage stage;
    private final ObjectStorage objectStorage;
    private final List<IndirectInstancer> instancers = new ArrayList<>();
    private final List<IndirectInstancer> activeInstancers = new ArrayList<>();
    private Submission[] submissions = new Submission[16];
    private Submission[] depthSubmissions = new Submission[16];
    private int submissionCount;
    private int depthSubmissionCount;

    private final ResizableStorageBuffer indirectBuffer = new ResizableStorageBuffer();
    private final ResizableStorageBuffer modelMetaBuffer = new ResizableStorageBuffer();
    private final ResizableStorageBuffer visListBuffer = new ResizableStorageBuffer();
    private final ResizableStorageBuffer scratchVisListBuffer = new ResizableStorageBuffer();
    private final ResizableStorageBuffer histogramBuffer = new ResizableStorageBuffer();

    private int liveDrawCount;
    private int totalInstanceCount;
    private int sortableDrawCount;
    private int maxSortableInstancesPerDraw;
    private int maxNumTiles;

    public IndirectStageGroup(Material.Stage stage) {
        this.stage = stage;
        this.objectStorage = new ObjectStorage(AbstractInstancer.STD140_BYTES);
    }

    public Material.Stage stage() {
        return stage;
    }

    public List<IndirectInstancer> instancers() {
        return instancers;
    }

    public List<IndirectInstancer> activeInstancers() {
        return activeInstancers;
    }

    public int submissionCount() {
        return submissionCount;
    }

    public Submission submission(int index) {
        return submissions[index];
    }

    public int depthSubmissionCount() {
        return depthSubmissionCount;
    }

    public Submission depthSubmission(int index) {
        return depthSubmissions[index];
    }

    public int objectBufferHandle() {
        return objectStorage.objectBuffer.handle();
    }

    public int pageDescriptorBufferHandle() {
        return objectStorage.frameDescriptorBuffer.handle();
    }

    public int indirectBufferHandle() {
        return indirectBuffer.handle();
    }

    public int visListBufferHandle() {
        return visListBuffer.handle();
    }

    public int modelMetaBufferHandle() {
        return modelMetaBuffer.handle();
    }

    public int scratchVisListBufferHandle() {
        return scratchVisListBuffer.handle();
    }

    public int histogramBufferHandle() {
        return histogramBuffer.handle();
    }

    // Page capacity = ObjectStorage's high-water mark (top of the sparse free-list arena).
    // Cull dispatches (pageCapacity, 1, 1); freed pages have descriptor.validBits==0 so the
    // workgroup exits before any lane does cull work.
    public int pageCapacity() {
        return objectStorage.capacity();
    }

    public int liveDrawCount() {
        return liveDrawCount;
    }

    public int totalInstanceCount() {
        return totalInstanceCount;
    }

    public int sortableDrawCount() {
        return sortableDrawCount;
    }

    public int maxSortableInstancesPerDraw() {
        return maxSortableInstancesPerDraw;
    }

    public int maxNumTiles() {
        return maxNumTiles;
    }

    synchronized void addInstancer(IndirectInstancer instancer) {
        instancers.add(instancer);
        instancer.attachMapping(objectStorage.createMapping());
    }

    // Async per-frame prep. Filters to instancers with any visible bits, sorts them into
    // stable draw order, assigns drawIndex + visList base, drains inactive instancers' pages,
    // and rebuilds submission spans.
    public synchronized void prepareFrame() {
        activeInstancers.clear();
        submissionCount = 0;
        depthSubmissionCount = 0;
        liveDrawCount = 0;
        totalInstanceCount = 0;
        sortableDrawCount = 0;
        maxSortableInstancesPerDraw = 0;
        maxNumTiles = 0;

        for (IndirectInstancer instancer : instancers) {
            Mesh mesh = MeshPool.mesh(instancer.meshHandle());
            if (instancer.liveCount() > 0 && instancer.hasVisibleInstances() && !mesh.empty()) {
                activeInstancers.add(instancer);
            } else {
                instancer.deactivate();
            }
        }
        if (activeInstancers.isEmpty()) {
            return;
        }
        activeInstancers.sort(DRAW_ORDER);

        liveDrawCount = activeInstancers.size();
        int baseInstance = 0;
        for (int drawIndex = 0; drawIndex < liveDrawCount; drawIndex++) {
            IndirectInstancer instancer = activeInstancers.get(drawIndex);
            int live = instancer.visibleCount();
            instancer.update(drawIndex, baseInstance);
            totalInstanceCount += live;
            baseInstance += live;
            if (instancer.material().requiresDepthSort) {
                sortableDrawCount++;
                if (live > maxSortableInstancesPerDraw) {
                    maxSortableInstancesPerDraw = live;
                }
            }
            appendSubmission(false, instancer, drawIndex);
            if (instancer.material().requiresFabulousDepth) {
                appendSubmission(true, instancer, drawIndex);
            }
        }
        if (sortableDrawCount > 0 && maxSortableInstancesPerDraw > IndirectSortProgram.TILE_SIZE) {
            maxNumTiles = (maxSortableInstancesPerDraw + IndirectSortProgram.TILE_SIZE - 1) / IndirectSortProgram.TILE_SIZE;
        }
    }

    // Per-frame uploads through the shared StagingBuffer. Caller is responsible for invoking
    // staging.flush() after all stage groups have uploaded.
    public synchronized void upload(StagingBuffer staging) {
        if (liveDrawCount == 0) return;

        objectStorage.ensureBufferCapacity();
        int objectVbo = objectStorage.objectBuffer.handle();
        for (IndirectInstancer instancer : activeInstancers) {
            // Instance data bypasses the staging ring: setters wrote directly into each
            // instancer's persistent-mapped SlabBuffer, and uploadInstances issues
            // glFlushMappedNamedBufferRange + glCopyNamedBufferSubData per dirty page run.
            instancer.uploadInstances(objectVbo);
        }
        objectStorage.uploadDescriptors(staging);

        long metaBytes = (long) liveDrawCount * MODEL_META_BYTES;
        modelMetaBuffer.ensureCapacity(metaBytes);
        staging.enqueueCopy(metaBytes, modelMetaBuffer.handle(), 0L, ptr -> writeModelMeta(ptr, metaBytes));

        long indirectBytes = (long) liveDrawCount * INDIRECT_CMD_BYTES;
        indirectBuffer.ensureCapacity(indirectBytes);
        staging.enqueueCopy(indirectBytes, indirectBuffer.handle(), 0L, ptr -> writeIndirect(ptr, indirectBytes));

        // visList / scratch / histogram are GPU-only: no CPU upload, only ensureCapacity.
        if (totalInstanceCount > 0) {
            long visListBytes = (long) totalInstanceCount * VIS_LIST_ENTRY_BYTES;
            visListBuffer.ensureCapacity(visListBytes);
            if (sortableDrawCount > 0 && maxSortableInstancesPerDraw > IndirectSortProgram.TILE_SIZE) {
                scratchVisListBuffer.ensureCapacity(visListBytes);
                long histoBytes = (long) liveDrawCount * IndirectSortProgram.BUCKETS * maxNumTiles * Integer.BYTES;
                histogramBuffer.ensureCapacity(histoBytes);
            }
        }
    }

    public synchronized void close() {
        for (IndirectInstancer instancer : instancers) {
            instancer.close();
        }
        instancers.clear();
        activeInstancers.clear();
        submissions = new Submission[16];
        depthSubmissions = new Submission[16];
        submissionCount = 0;
        depthSubmissionCount = 0;
        objectStorage.delete();
        indirectBuffer.delete();
        modelMetaBuffer.delete();
        visListBuffer.delete();
        scratchVisListBuffer.delete();
        histogramBuffer.delete();
        liveDrawCount = 0;
        totalInstanceCount = 0;
        sortableDrawCount = 0;
        maxSortableInstancesPerDraw = 0;
        maxNumTiles = 0;
    }

    private void writeModelMeta(long ptr, long expectedBytes) {
        long off = 0L;
        for (int drawIndex = 0; drawIndex < liveDrawCount; drawIndex++) {
            IndirectInstancer instancer = activeInstancers.get(drawIndex);
            Mesh mesh = MeshPool.mesh(instancer.meshHandle());
            int live = instancer.visibleCount();
            int flags = instancer.material().requiresDepthSort ? 1 : 0;
            MemoryUtil.memPutInt(ptr + off,       0);
            MemoryUtil.memPutInt(ptr + off + 4,   instancer.baseInstance());
            MemoryUtil.memPutInt(ptr + off + 8,   live);
            MemoryUtil.memPutInt(ptr + off + 12,  flags);
            MemoryUtil.memPutFloat(ptr + off + 16, mesh.boundsX());
            MemoryUtil.memPutFloat(ptr + off + 20, mesh.boundsY());
            MemoryUtil.memPutFloat(ptr + off + 24, mesh.boundsZ());
            MemoryUtil.memPutFloat(ptr + off + 28, mesh.boundsRadius());
            off += MODEL_META_BYTES;
        }
        if (off != expectedBytes) {
            throw new IllegalStateException("model meta size mismatch: " + off + " vs " + expectedBytes);
        }
    }

    private void writeIndirect(long ptr, long expectedBytes) {
        long off = 0L;
        for (int drawIndex = 0; drawIndex < liveDrawCount; drawIndex++) {
            IndirectInstancer instancer = activeInstancers.get(drawIndex);
            Mesh mesh = MeshPool.mesh(instancer.meshHandle());
            MemoryUtil.memPutInt(ptr + off,      mesh.indexCount());
            MemoryUtil.memPutInt(ptr + off + 4,  0);
            MemoryUtil.memPutInt(ptr + off + 8,  mesh.firstIndex());
            MemoryUtil.memPutInt(ptr + off + 12, mesh.baseVertex());
            MemoryUtil.memPutInt(ptr + off + 16, instancer.baseInstance());
            off += INDIRECT_CMD_BYTES;
        }
        if (off != expectedBytes) {
            throw new IllegalStateException("indirect cmd size mismatch: " + off + " vs " + expectedBytes);
        }
    }

    private void appendSubmission(boolean depth, IndirectInstancer instancer, int drawIndex) {
        Submission[] dst = depth ? depthSubmissions : submissions;
        int count = depth ? depthSubmissionCount : submissionCount;
        if (count > 0) {
            Submission last = dst[count - 1];
            if (last.material == instancer.material() && last.texture.equals(instancer.texture())) {
                last.drawCount++;
                return;
            }
        }
        dst = ensureSubmissionCapacity(dst, count + 1);
        Submission submission = dst[count];
        if (submission == null) {
            submission = new Submission(instancer.material(), instancer.texture(), drawIndex);
            dst[count] = submission;
        } else {
            submission.reset(instancer.material(), instancer.texture(), drawIndex);
        }
        if (depth) {
            depthSubmissions = dst;
            depthSubmissionCount = count + 1;
        } else {
            submissions = dst;
            submissionCount = count + 1;
        }
    }

    private static Submission[] ensureSubmissionCapacity(Submission[] src, int required) {
        if (required <= src.length) return src;
        return Arrays.copyOf(src, Math.max(required, src.length << 1));
    }

    public static final class Submission {
        private Material material;
        private Identifier texture;
        private int firstDrawIndex;
        private int drawCount = 1;

        Submission(Material material, Identifier texture, int firstDrawIndex) {
            reset(material, texture, firstDrawIndex);
        }

        void reset(Material material, Identifier texture, int firstDrawIndex) {
            this.material = material;
            this.texture = texture;
            this.firstDrawIndex = firstDrawIndex;
            this.drawCount = 1;
        }

        public Material material() {
            return material;
        }

        public Identifier texture() {
            return texture;
        }

        public int firstDrawIndex() {
            return firstDrawIndex;
        }

        public int drawCount() {
            return drawCount;
        }
    }
}
//?}
