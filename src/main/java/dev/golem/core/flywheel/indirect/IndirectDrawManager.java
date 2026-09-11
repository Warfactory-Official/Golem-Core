package dev.golem.core.flywheel.indirect;

//? if neoforge && >=26.1 {

import dev.golem.core.GolemCore;
import dev.golem.core.flywheel.*;
import dev.golem.core.flywheel.instance.CrumblingScratch;
import dev.golem.core.flywheel.plan.ForEachPlan;
import dev.golem.core.flywheel.plan.Plan;
import dev.golem.core.flywheel.visual.DynamicVisual;
import dev.golem.core.flywheel.visual.GolemRenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

// INDIRECT backend draw manager. Per frame:
//   1. The async frame plan compacts instancer pages and prepares stage draw snapshots after
//      visual writes finish. flushAll() only reclaims fenced staging regions, uploads the
//      prepared pages/descriptors/model meta/commands, then drains the ring.
//   2. drawStage() rebuilds HiZ for that stage, dispatches page-based cull once for the
//      whole stage, dispatches an apply pass to copy model survivor counts into the
//      indirect command buffer, optionally sorts translucent visList slices, then submits
//      contiguous draw ranges grouped by (material, texture).
public final class IndirectDrawManager implements DrawManager {

    private static final int SSBO_INSTANCES_BINDING = 0;
    private static final int SSBO_VISLIST_BINDING = 1;
    private static final int SSBO_MODELS_BINDING = 2;
    private static final int SSBO_COMMANDS_BINDING = 3;
    private static final int SSBO_HISTOGRAM_BINDING = 4;
    private static final int SSBO_SCRATCH_BINDING = 5;
    private static final int SSBO_PAGE_DESCRIPTORS_BINDING = 6;
    private static final int APPLY_LOCAL_SIZE_X = 64;
    private static final Collection<RenderPass.Draw<Void>> NO_DRAWS = Collections.emptyList();
    private static final Collection<String> NO_DYNAMIC_UNIFORMS = Collections.emptyList();

    private final Map<InstancerKey, IndirectInstancer> instancers = new Object2ObjectOpenHashMap<>();
    private final IndirectStageGroup[] stageGroups = new IndirectStageGroup[Material.Stage.values().length];
    private final Plan<DynamicVisual.Context> frameMaintenancePlan = ForEachPlan
            .of(context -> instancerSnapshot(), IndirectInstancer::parallelUpdate)
            .then(ForEachPlan.of(context -> stageGroups, (group, context) -> group.prepareFrame()));
    private final IndirectComputeProgram cullProgram = new IndirectComputeProgram();
    private final IndirectApplyProgram applyProgram = new IndirectApplyProgram();
    private final IndirectScatterProgram scatterProgram = new IndirectScatterProgram();
    private final IndirectSortProgram sortProgram = new IndirectSortProgram();
    private final IndirectHizProgram hizProgram = new IndirectHizProgram();
    private final HizPyramid hizPyramid = new HizPyramid();
    private final IndirectCrumblingScratch crumblingScratch = new IndirectCrumblingScratch();
    private final float[] frustumPlanes = new float[24];
    private volatile boolean sortDumpRequested;
    private boolean preflightFailed;
    private int lastFlushedFrameId = Integer.MIN_VALUE;
    private StagingBuffer staging;

    public IndirectDrawManager() {
        for (Material.Stage stage : Material.Stage.values()) {
            stageGroups[stage.ordinal()] = new IndirectStageGroup(stage);
        }
    }

    @Override
    public Plan<DynamicVisual.Context> frameMaintenancePlan() {
        return frameMaintenancePlan;
    }

    @Override
    public boolean canDraw(Material material) {
        if (preflightFailed) return false;
        boolean ready = DrawManager.super.canDraw(material)
                && scatterProgram.ensureLoaded()
                && cullProgram.ensureLoaded()
                && applyProgram.ensureLoaded()
                && (material.stage != Material.Stage.TRANSLUCENT || sortProgram.ensureLoaded());
        preflightFailed = !ready;
        return ready;
    }

    @Override
    public synchronized Instancer instancer(int meshHandle, Material material, Identifier texture) {
        if (material == Material.CRUMBLING) {
            throw new IllegalArgumentException("CRUMBLING is rendered per-frame against existing instancers, not allocated as its own bucket");
        }
        InstancerKey key = new InstancerKey(meshHandle, material, texture);
        IndirectInstancer instancer = instancers.get(key);
        if (instancer != null) return instancer;
        IndirectStageGroup group = stageGroup(material.stage);
        instancer = new IndirectInstancer(meshHandle, material, texture, group);
        group.addInstancer(instancer);
        instancers.put(key, instancer);
        lastFlushedFrameId = Integer.MIN_VALUE;
        return instancer;
    }

    @Override
    public synchronized void flushAll() {
        int frameId = GolemRenderSystem.currentFrameId();
        if (frameId == lastFlushedFrameId) return;
        StagingBuffer staging = ensureStaging();
        staging.reclaim();
        for (IndirectStageGroup group : stageGroups) {
            group.upload(staging);
        }
        staging.flush();
        lastFlushedFrameId = frameId;
    }

    @Override
    public CrumblingScratch crumblingScratch() {
        return null;
    }

    @Override
    public void drawStage(Material.Stage stage, FrameState state) {
        IndirectStageGroup group = stageGroup(stage);
        if (group.liveDrawCount() == 0 || group.pageCapacity() == 0) return;

        GpuBuffer vertexBuffer = MeshPool.vertexBuffer();
        GpuBuffer indexBuffer = MeshPool.indexBuffer();
        if (vertexBuffer == null || indexBuffer == null) return;

        if (!cullProgram.ensureLoaded() || !applyProgram.ensureLoaded()) return;

        Minecraft minecraft = state.minecraft();
        RenderTarget target = renderTargetForStage(minecraft, stage);

        // flushAll fills the cull/draw inputs two ways: the scatter compute writes
        // descriptorBuffer, modelMetaBuffer, and indirectBuffer (SSBO stores), while instance
        // data is copied into objectBuffer with glCopyNamedBufferSubData (a buffer-update).
        // Cull reads all of them, so the barrier must cover both write classes — SHADER_STORAGE
        // for the scatter stores, BUFFER_UPDATE for the copy. Without BUFFER_UPDATE, cull may
        // observe the prior frame's objectBuffer on drivers that don't auto-sync copy→SSBO read.
        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_BUFFER_UPDATE_BARRIER_BIT);

        extractFrustumPlanes(state.viewProjection(), frustumPlanes);
        boolean hizReady = buildHizPyramid(target);
        if (!dispatchCullPass(group, state, hizReady)) return;

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        dispatchApplyPass(group);

        boolean dumpThisStage = sortDumpRequested && stage == Material.Stage.TRANSLUCENT;
        if (dumpThisStage) {
            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL43.GL_COMMAND_BARRIER_BIT);
            dumpVisList("PRE-SORT", group);
        }

        boolean sortDispatched = false;
        if (stage == Material.Stage.TRANSLUCENT) {
            sortDispatched = dispatchSortPass(group);
        }
        if (!sortDispatched) {
            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL43.GL_COMMAND_BARRIER_BIT);
        }

        if (dumpThisStage) {
            dumpVisList("POST-SORT", group);
            sortDumpRequested = false;
        }

        TextureManager textureManager = minecraft.getTextureManager();
        String passLabel = "Golem indirect " + stage.name().toLowerCase();

        Map<Identifier, AbstractTexture> resolvedTextures = new Object2ObjectOpenHashMap<>();
        for (int i = 0; i < group.submissionCount(); i++) {
            IndirectStageGroup.Submission submission = group.submission(i);
            Identifier tex = submission.texture();
            if (!resolvedTextures.containsKey(tex)) {
                resolvedTextures.put(tex, textureManager.getTexture(tex));
            }
        }

        minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> passLabel,
                        target.getColorTextureView(), OptionalInt.empty(),
                        target.getDepthTextureView(), OptionalDouble.empty())) {

            RenderSystem.bindDefaultUniforms(pass);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setIndexBuffer(indexBuffer, VertexFormat.IndexType.INT);
            pass.bindTexture("Sampler1",
                    minecraft.gameRenderer.overlayTexture().getTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.bindTexture("Sampler2",
                    minecraft.gameRenderer.lightmap(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("DynamicTransforms", state.transforms());
            pass.setUniform("ShaderLights", state.shaderLights());

            int objectHandle = group.objectBufferHandle();
            int visListHandle = group.visListBufferHandle();
            int indirectHandle = group.indirectBufferHandle();
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_INSTANCES_BINDING, objectHandle);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_VISLIST_BINDING, visListHandle);
            GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, indirectHandle);

            RenderPipeline boundPipeline = null;
            Identifier boundTexture = null;
            for (int i = 0; i < group.submissionCount(); i++) {
                IndirectStageGroup.Submission submission = group.submission(i);
                RenderPipeline pipeline = IndirectPipelines.pipelineFor(submission.material());
                boolean pipelineChanged = pipeline != boundPipeline;
                if (pipelineChanged) {
                    pass.setPipeline(pipeline);
                    boundPipeline = pipeline;
                }
                Identifier texture = submission.texture();
                if (!texture.equals(boundTexture)) {
                    AbstractTexture sampler0 = resolvedTextures.get(texture);
                    pass.bindTexture("Sampler0", sampler0.getTextureView(), sampler0.getSampler());
                    boundTexture = texture;
                }
                setupIndirectPass(pass, indexBuffer, pipelineChanged);
                GL43.glMultiDrawElementsIndirect(
                        GL11.GL_TRIANGLES,
                        GL11.GL_UNSIGNED_INT,
                        (long) submission.firstDrawIndex() * IndirectStageGroup.INDIRECT_CMD_BYTES,
                        submission.drawCount(),
                        IndirectStageGroup.INDIRECT_CMD_BYTES);
            }

            if (stage == Material.Stage.TRANSLUCENT && Minecraft.useShaderTransparency()) {
                for (int i = 0; i < group.depthSubmissionCount(); i++) {
                    IndirectStageGroup.Submission submission = group.depthSubmission(i);
                    RenderPipeline depthPipeline = IndirectPipelines.depthOnlyPipelineFor(submission.material());
                    if (depthPipeline == null) continue;
                    boolean pipelineChanged = depthPipeline != boundPipeline;
                    if (pipelineChanged) {
                        pass.setPipeline(depthPipeline);
                        boundPipeline = depthPipeline;
                    }
                    Identifier texture = submission.texture();
                    if (!texture.equals(boundTexture)) {
                        AbstractTexture sampler0 = resolvedTextures.get(texture);
                        pass.bindTexture("Sampler0", sampler0.getTextureView(), sampler0.getSampler());
                        boundTexture = texture;
                    }
                    setupIndirectPass(pass, indexBuffer, pipelineChanged);
                    GL43.glMultiDrawElementsIndirect(
                            GL11.GL_TRIANGLES,
                            GL11.GL_UNSIGNED_INT,
                            (long) submission.firstDrawIndex() * IndirectStageGroup.INDIRECT_CMD_BYTES,
                            submission.drawCount(),
                            IndirectStageGroup.INDIRECT_CMD_BYTES);
                }
            }
        }
    }

    private static RenderTarget renderTargetForStage(Minecraft minecraft, Material.Stage stage) {
        RenderTarget target = minecraft.getMainRenderTarget();
        if (stage == Material.Stage.TRANSLUCENT && Minecraft.useShaderTransparency()) {
            RenderTarget translucentTarget = minecraft.levelRenderer.getTranslucentTarget();
            if (translucentTarget != null) target = translucentTarget;
        }
        return target;
    }

    private boolean buildHizPyramid(RenderTarget target) {
        var depthTex = target.getDepthTexture();
        if (depthTex == null) return false;
        int srcW = target.width;
        int srcH = target.height;
        if (!hizPyramid.ensureSized(srcW, srcH)) return false;
        if (!hizProgram.ensureLoaded()) return false;
        hizPyramid.rebuild(depthTex, hizProgram);
        return true;
    }

    private boolean dispatchCullPass(IndirectStageGroup group, FrameState state, boolean hizReady) {
        if (group.pageCapacity() == 0) return false;

        cullProgram.use();
        cullProgram.setFrustumPlanes(frustumPlanes);
        var camRO = state.cameraPosRO();
        cullProgram.setCameraPosRO(camRO.x(), camRO.y(), camRO.z());
        cullProgram.setModelView(state.modelView());

        Minecraft mc = state.minecraft();
        var cs = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Matrix4fc proj = cs.projectionMatrix;
        float znear = -proj.m32() / (1.0F - proj.m22());
        cullProgram.setProjectionInfo(znear, proj.m00(), proj.m11());
        if (hizPyramid.textureId() != 0) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, hizPyramid.textureId());
            cullProgram.setPyramidInfo(hizPyramid.width(), hizPyramid.height(), hizPyramid.mipLevels() - 1);
        }
        cullProgram.setHizEnabled(hizReady);

        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_INSTANCES_BINDING, group.objectBufferHandle());
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_VISLIST_BINDING, group.visListBufferHandle());
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_MODELS_BINDING, group.modelMetaBufferHandle());
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_PAGE_DESCRIPTORS_BINDING, group.pageDescriptorBufferHandle());
        GL43.glDispatchCompute(group.pageCapacity(), 1, 1);
        return true;
    }

    private void dispatchApplyPass(IndirectStageGroup group) {
        if (group.liveDrawCount() == 0) return;
        applyProgram.use();
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_MODELS_BINDING, group.modelMetaBufferHandle());
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_COMMANDS_BINDING, group.indirectBufferHandle());
        int groupsX = (group.liveDrawCount() + APPLY_LOCAL_SIZE_X - 1) / APPLY_LOCAL_SIZE_X;
        GL43.glDispatchCompute(groupsX, 1, 1);
    }

    private boolean dispatchSortPass(IndirectStageGroup group) {
        if (group.sortableDrawCount() == 0) return false;

        boolean useBitonic = group.maxSortableInstancesPerDraw() <= IndirectSortProgram.TILE_SIZE;
        if (!sortProgram.ensureLoaded()) return false;

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

        int visHandle = group.visListBufferHandle();
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_MODELS_BINDING, group.modelMetaBufferHandle());
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_COMMANDS_BINDING, group.indirectBufferHandle());

        if (useBitonic) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_VISLIST_BINDING, visHandle);
            sortProgram.useBitonic();
            GL43.glDispatchCompute(1, group.liveDrawCount(), 1);
        } else {
            int scratchHandle = group.scratchVisListBufferHandle();
            int histHandle = group.histogramBufferHandle();
            int numTiles = group.maxNumTiles();
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_HISTOGRAM_BINDING, histHandle);
            for (int pass = 0; pass < IndirectSortProgram.PASSES; pass++) {
                int shift = IndirectSortProgram.KEY_SHIFT_BASE + pass * 8;
                int srcHandle = (pass & 1) == 0 ? visHandle : scratchHandle;
                int dstHandle = (pass & 1) == 0 ? scratchHandle : visHandle;
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_VISLIST_BINDING, srcHandle);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_SCRATCH_BINDING, dstHandle);

                sortProgram.useHist(shift, numTiles);
                GL43.glDispatchCompute(numTiles, group.liveDrawCount(), 1);
                GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

                sortProgram.useScan(numTiles);
                GL43.glDispatchCompute(group.liveDrawCount(), 1, 1);
                GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

                sortProgram.useScatter(shift, numTiles);
                GL43.glDispatchCompute(numTiles, group.liveDrawCount(), 1);
                if (pass < IndirectSortProgram.PASSES - 1) {
                    GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
                }
            }
        }

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL43.GL_COMMAND_BARRIER_BIT);
        return true;
    }

    public void requestSortDump() {
        sortDumpRequested = true;
    }

    private void dumpVisList(String label, IndirectStageGroup group) {
        int numDraws = group.liveDrawCount();
        if (numDraws == 0) return;

        ByteBuffer cmdBuf = MemoryUtil.memAlloc(numDraws * IndirectStageGroup.INDIRECT_CMD_BYTES)
                .order(ByteOrder.nativeOrder());
        try {
            GL45C.glGetNamedBufferSubData(group.indirectBufferHandle(), 0L, cmdBuf);

            int maxEnd = 0;
            for (int d = 0; d < numDraws; d++) {
                int base = cmdBuf.getInt(d * IndirectStageGroup.INDIRECT_CMD_BYTES + 16);
                int count = cmdBuf.getInt(d * IndirectStageGroup.INDIRECT_CMD_BYTES + 4);
                maxEnd = Math.max(maxEnd, base + count);
            }
            if (maxEnd == 0) return;

            ByteBuffer visBuf = MemoryUtil.memAlloc(maxEnd * IndirectStageGroup.VIS_LIST_ENTRY_BYTES)
                    .order(ByteOrder.nativeOrder());
            try {
                GL45C.glGetNamedBufferSubData(group.visListBufferHandle(), 0L, visBuf);
                StringBuilder sb = new StringBuilder();
                sb.append('\n').append(label)
                        .append(" stage=").append(group.stage())
                        .append(" numDraws=").append(numDraws)
                        .append(" pageCapacity=").append(group.pageCapacity())
                        .append(" maxNumTiles=").append(group.maxNumTiles())
                        .append('\n');
                for (int d = 0; d < numDraws; d++) {
                    IndirectInstancer instancer = group.activeInstancers().get(d);
                    if (!instancer.material().requiresDepthSort) continue;
                    int base = cmdBuf.getInt(d * IndirectStageGroup.INDIRECT_CMD_BYTES + 16);
                    int count = cmdBuf.getInt(d * IndirectStageGroup.INDIRECT_CMD_BYTES + 4);
                    int minKey = 0xFFFF;
                    int maxKey = 0;
                    int violations = 0;
                    int prevKey = -1;
                    for (int i = 0; i < count; i++) {
                        int o = (base + i) * IndirectStageGroup.VIS_LIST_ENTRY_BYTES;
                        int key = (visBuf.getInt(o) >>> 16) & 0xFFFF;
                        if (i > 0 && key < prevKey) violations++;
                        prevKey = key;
                        if (key < minKey) minKey = key;
                        if (key > maxKey) maxKey = key;
                    }
                    sb.append("  draw=").append(d)
                            .append(" material=").append(instancer.material())
                            .append(" tex=").append(instancer.texture())
                            .append(" base=").append(base)
                            .append(" count=").append(count)
                            .append(" ascendingViolations=").append(violations)
                            .append(String.format(" keyRange=[0x%04X..0x%04X]", minKey, maxKey))
                            .append('\n');
                    int dumpHead = Math.min(count, 8);
                    for (int i = 0; i < dumpHead; i++) {
                        int o = (base + i) * IndirectStageGroup.VIS_LIST_ENTRY_BYTES;
                        int key = (visBuf.getInt(o) >>> 16) & 0xFFFF;
                        int inst = visBuf.getInt(o + 4);
                        sb.append(String.format("    head[%d] key=0x%04X inst=%d%n", i, key, inst));
                    }
                    if (count > 16) {
                        int tailStart = Math.max(dumpHead, count - 8);
                        sb.append("    ...\n");
                        for (int i = tailStart; i < count; i++) {
                            int o = (base + i) * IndirectStageGroup.VIS_LIST_ENTRY_BYTES;
                            int key = (visBuf.getInt(o) >>> 16) & 0xFFFF;
                            int inst = visBuf.getInt(o + 4);
                            sb.append(String.format("    tail[%d] key=0x%04X inst=%d%n", i, key, inst));
                        }
                    }
                }
                GolemCore.LOG.info(sb.toString());
            } finally {
                MemoryUtil.memFree(visBuf);
            }
        } finally {
            MemoryUtil.memFree(cmdBuf);
        }
    }

    private static void extractFrustumPlanes(Matrix4fc m, float[] dst24) {
        plane(dst24, 0, m.m03() + m.m00(), m.m13() + m.m10(), m.m23() + m.m20(), m.m33() + m.m30());
        plane(dst24, 1, m.m03() - m.m00(), m.m13() - m.m10(), m.m23() - m.m20(), m.m33() - m.m30());
        plane(dst24, 2, m.m03() + m.m01(), m.m13() + m.m11(), m.m23() + m.m21(), m.m33() + m.m31());
        plane(dst24, 3, m.m03() - m.m01(), m.m13() - m.m11(), m.m23() - m.m21(), m.m33() - m.m31());
        plane(dst24, 4, m.m03() + m.m02(), m.m13() + m.m12(), m.m23() + m.m22(), m.m33() + m.m32());
        plane(dst24, 5, m.m03() - m.m02(), m.m13() - m.m12(), m.m23() - m.m22(), m.m33() - m.m32());
    }

    private static void plane(float[] dst, int idx, float a, float b, float c, float d) {
        float invLen = 1.0F / (float) Math.sqrt(a * a + b * b + c * c);
        int o = idx * 4;
        dst[o] = a * invLen;
        dst[o + 1] = b * invLen;
        dst[o + 2] = c * invLen;
        dst[o + 3] = d * invLen;
    }

    @Override
    public void drawCrumbling(java.util.List<CrumblingDraw> draws, FrameState state) {
        if (draws.isEmpty()) return;

        GpuBuffer vertexBuffer = MeshPool.vertexBuffer();
        GpuBuffer indexBuffer = MeshPool.indexBuffer();
        if (vertexBuffer == null || indexBuffer == null) return;

        IndirectCrumblingScratch.Plan plan = crumblingScratch.build(draws);
        if (plan.isEmpty()) return;

        Minecraft minecraft = state.minecraft();
        TextureManager textureManager = minecraft.getTextureManager();
        Map<Identifier, AbstractTexture> resolvedStages = new Object2ObjectOpenHashMap<>();
        for (var bucket : plan.buckets()) {
            int p = bucket.progress;
            if (p < 0 || p >= ModelBakery.BREAKING_LOCATIONS.size()) continue;
            Identifier id = ModelBakery.BREAKING_LOCATIONS.get(p);
            if (!resolvedStages.containsKey(id)) {
                resolvedStages.put(id, textureManager.getTexture(id));
            }
        }

        var target = minecraft.getMainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "Golem indirect crumbling",
                        target.getColorTextureView(), OptionalInt.empty(),
                        target.getDepthTextureView(), OptionalDouble.empty())) {

            RenderSystem.bindDefaultUniforms(pass);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setIndexBuffer(indexBuffer, VertexFormat.IndexType.INT);
            pass.setUniform("DynamicTransforms", state.transforms());
            pass.setPipeline(IndirectPipelines.pipelineFor(Material.CRUMBLING));

            int visListHandle = ((GlBuffer) plan.visListBuffer()).handle;
            int indirectHandle = ((GlBuffer) plan.indirectBuffer()).handle;
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_VISLIST_BINDING, visListHandle);
            GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, indirectHandle);

            boolean boundVertexArray = false;
            for (var bucket : plan.buckets()) {
                if (bucket.drawCount() == 0) continue;
                int p = bucket.progress;
                if (p < 0 || p >= ModelBakery.BREAKING_LOCATIONS.size()) continue;
                Identifier stageTex = ModelBakery.BREAKING_LOCATIONS.get(p);
                AbstractTexture abstractTexture = resolvedStages.get(stageTex);
                if (abstractTexture == null) continue;
                pass.bindTexture("Sampler0", abstractTexture.getTextureView(), abstractTexture.getSampler());
                setupIndirectPass(pass, indexBuffer, !boundVertexArray);
                boundVertexArray = true;

                int objectHandle = bucket.sourceGroup.objectBufferHandle();
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_INSTANCES_BINDING, objectHandle);
                GL43.glMultiDrawElementsIndirect(
                        GL11.GL_TRIANGLES,
                        GL11.GL_UNSIGNED_INT,
                        (long) bucket.firstDrawIndex() * IndirectStageGroup.INDIRECT_CMD_BYTES,
                        bucket.drawCount(),
                        IndirectStageGroup.INDIRECT_CMD_BYTES);
            }
        }
    }

    private static void setupIndirectPass(RenderPass pass, GpuBuffer indexBuffer, boolean bindVertexArray) {
        if (bindVertexArray) {
            pass.drawIndexed(0, 0, 0, 0);
        } else {
            pass.drawMultipleIndexed(NO_DRAWS, indexBuffer, VertexFormat.IndexType.INT, NO_DYNAMIC_UNIFORMS, null);
        }
    }

    @Override
    public synchronized void close() {
        for (IndirectStageGroup group : stageGroups) {
            group.close();
        }
        instancers.clear();
        cullProgram.close();
        applyProgram.close();
        scatterProgram.close();
        sortProgram.close();
        hizProgram.close();
        hizPyramid.close();
        crumblingScratch.close();
        if (staging != null) {
            staging.delete();
            staging = null;
        }
    }

    private IndirectStageGroup stageGroup(Material.Stage stage) {
        return stageGroups[stage.ordinal()];
    }

    private synchronized IndirectInstancer[] instancerSnapshot() {
        IndirectInstancer[] snapshot = new IndirectInstancer[instancers.size()];
        int i = 0;
        for (IndirectInstancer instancer : instancers.values()) {
            snapshot[i++] = instancer;
        }
        return snapshot;
    }

    private StagingBuffer ensureStaging() {
        if (staging != null) return staging;
        if (!scatterProgram.ensureLoaded()) {
            throw new IllegalStateException("Golem scatter compute shader is unavailable");
        }
        staging = new StagingBuffer(scatterProgram);
        return staging;
    }

    private record InstancerKey(int meshHandle, Material material, Identifier texture) {
        InstancerKey {
            Objects.requireNonNull(material);
            Objects.requireNonNull(texture);
        }
    }
}
//?}
