package dev.golem.core.flywheel.instance;

//? if >=26.1 {

import dev.golem.core.flywheel.CrumblingDraw;
import dev.golem.core.flywheel.DrawStats;
import dev.golem.core.flywheel.DrawManager;
import dev.golem.core.flywheel.FrameState;
import dev.golem.core.flywheel.Material;
import dev.golem.core.flywheel.Mesh;
import dev.golem.core.flywheel.MeshPool;
import dev.golem.core.flywheel.Pipelines;
import dev.golem.core.flywheel.visual.GolemRenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class InstancedDrawManager implements DrawManager {

    private final Map<Key, InstancedInstancer> instancers = new Object2ObjectOpenHashMap<>();
    private final InstancedStageGroup[] stageGroups = new InstancedStageGroup[Material.Stage.values().length];
    private final CrumblingScratch crumblingScratch = new CrumblingScratch();
    private int lastFlushedFrameId = Integer.MIN_VALUE;

    public InstancedDrawManager() {
        for (Material.Stage stage : Material.Stage.values()) {
            stageGroups[stage.ordinal()] = new InstancedStageGroup(stage);
        }
    }

    @Override
    public synchronized InstancedInstancer instancer(int meshHandle, Material material, Identifier texture) {
        Key key = new Key(meshHandle, material, texture);
        InstancedInstancer existing = instancers.get(key);
        if (existing != null) return existing;
        InstancedInstancer fresh = new InstancedInstancer(meshHandle, material, texture);
        instancers.put(key, fresh);
        stageGroup(material.stage).addInstancer(fresh);
        lastFlushedFrameId = Integer.MIN_VALUE;
        return fresh;
    }

    public synchronized Iterable<InstancedInstancer> instancers() {
        return instancers.values();
    }

    @Override
    public synchronized void flushAll() {
        int frameId = GolemRenderSystem.currentFrameId();
        if (frameId == lastFlushedFrameId) return;
        for (InstancedStageGroup group : stageGroups) {
            group.flushAll();
        }
        lastFlushedFrameId = frameId;
    }

    @Override
    public CrumblingScratch crumblingScratch() {
        return crumblingScratch;
    }

    @Override
    public void drawStage(Material.Stage stage, FrameState state) {
        InstancedStageGroup group = stageGroup(stage);
        if (group.activeInstancers().isEmpty()) return;

        GpuBuffer vertexBuffer = MeshPool.vertexBuffer();
        GpuBuffer indexBuffer = MeshPool.indexBuffer();
        if (vertexBuffer == null || indexBuffer == null) return;

        Minecraft minecraft = state.minecraft();
        TextureManager textureManager = minecraft.getTextureManager();
        String passLabel = "Golem flywheel " + stage.name().toLowerCase();
        Map<Identifier, AbstractTexture> resolvedTextures = new Object2ObjectOpenHashMap<>();
        for (InstancedStageGroup.Submission submission : group.submissions()) {
            Identifier tex = submission.texture();
            if (!resolvedTextures.containsKey(tex)) {
                resolvedTextures.put(tex, textureManager.getTexture(tex));
            }
        }

        minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);
        var target = minecraft.getMainRenderTarget();
        if (stage == Material.Stage.TRANSLUCENT && Minecraft.useShaderTransparency()) {
            var translucentTarget = minecraft.levelRenderer.getTranslucentTarget();
            if (translucentTarget != null) target = translucentTarget;
        }
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

            List<InstancedInstancer> activeInstancers = group.activeInstancers();
            for (InstancedStageGroup.Submission submission : group.submissions()) {
                pass.setPipeline(Pipelines.pipelineFor(submission.material()));
                AbstractTexture sampler0 = resolvedTextures.get(submission.texture());
                pass.bindTexture("Sampler0", sampler0.getTextureView(), sampler0.getSampler());

                int start = submission.firstInstancerIndex();
                int end = start + submission.instancerCount();
                for (int i = start; i < end; i++) {
                    InstancedInstancer instancer = activeInstancers.get(i);
                    int live = instancer.liveCount();
                    GpuBuffer ubo = instancer.gpuBuffer();
                    if (live == 0 || ubo == null) continue;
                    Mesh mesh = MeshPool.mesh(instancer.meshHandle());
                    if (mesh.empty()) continue;

                    int chunkCap = Pipelines.INSTANCE_CAP;
                    for (int chunkStart = 0; chunkStart < live; chunkStart += chunkCap) {
                        int chunkCount = Math.min(chunkCap, live - chunkStart);
                        long byteOffset = (long) chunkStart * AbstractInstancer.STD140_BYTES;
                        long byteLength = (long) chunkCount * AbstractInstancer.STD140_BYTES;
                        GpuBufferSlice slice = ubo.slice(byteOffset, byteLength);
                        pass.setUniform("Instances", slice);
                        pass.drawIndexed(mesh.baseVertex(), mesh.firstIndex(), mesh.indexCount(), chunkCount);
                        DrawStats.draw(1);
                    }
                }
            }
        }
    }

    @Override
    public void drawCrumbling(List<CrumblingDraw> draws, FrameState state) {
        if (draws.isEmpty()) return;

        GpuBuffer vertexBuffer = MeshPool.vertexBuffer();
        GpuBuffer indexBuffer = MeshPool.indexBuffer();
        if (vertexBuffer == null || indexBuffer == null) return;

        Minecraft minecraft = state.minecraft();
        TextureManager textureManager = minecraft.getTextureManager();
        Map<Identifier, AbstractTexture> resolvedStages = new Object2ObjectOpenHashMap<>();
        for (CrumblingDraw draw : draws) {
            int p = draw.progress();
            if (p < 0 || p >= ModelBakery.BREAKING_LOCATIONS.size()) continue;
            Identifier id = ModelBakery.BREAKING_LOCATIONS.get(p);
            if (!resolvedStages.containsKey(id)) {
                resolvedStages.put(id, textureManager.getTexture(id));
            }
        }

        List<List<Instance>> perGroupInstances = new ArrayList<>(draws.size());
        for (CrumblingDraw draw : draws) {
            perGroupInstances.add(draw.instances());
        }
        List<GpuBufferSlice> slices = crumblingScratch.fill(perGroupInstances);
        if (slices.isEmpty()) return;

        var target = minecraft.getMainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "Golem flywheel crumbling",
                        target.getColorTextureView(), OptionalInt.empty(),
                        target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setIndexBuffer(indexBuffer, VertexFormat.IndexType.INT);
            pass.setUniform("DynamicTransforms", state.transforms());
            pass.setPipeline(Pipelines.pipelineFor(Material.CRUMBLING));

            Identifier boundTexture = null;
            for (int g = 0; g < draws.size(); g++) {
                CrumblingDraw draw = draws.get(g);
                Mesh mesh = MeshPool.mesh(draw.meshHandle());
                if (mesh.empty()) continue;
                Identifier stageTex = ModelBakery.BREAKING_LOCATIONS.get(draw.progress());
                AbstractTexture abstractTexture = resolvedStages.get(stageTex);
                if (abstractTexture == null) continue;
                if (!stageTex.equals(boundTexture)) {
                    pass.bindTexture("Sampler0", abstractTexture.getTextureView(), abstractTexture.getSampler());
                    boundTexture = stageTex;
                }
                pass.setUniform("Instances", slices.get(g));
                pass.drawIndexed(mesh.baseVertex(), mesh.firstIndex(), mesh.indexCount(), draw.instances().size());
                DrawStats.draw(1);
            }
        }
    }

    @Override
    public synchronized void close() {
        for (InstancedInstancer i : instancers.values()) {
            i.close();
        }
        instancers.clear();
        for (InstancedStageGroup group : stageGroups) {
            group.close();
        }
        crumblingScratch.close();
    }

    private InstancedStageGroup stageGroup(Material.Stage stage) {
        return stageGroups[stage.ordinal()];
    }

    private record Key(int meshHandle, Material material, Identifier texture) {
        Key {
            Objects.requireNonNull(material);
            Objects.requireNonNull(texture);
        }
    }
}
//?}
