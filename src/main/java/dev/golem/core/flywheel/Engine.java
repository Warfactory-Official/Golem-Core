package dev.golem.core.flywheel;

//? if >=26.1 {

import dev.golem.core.flywheel.instance.Instance;
import dev.golem.core.flywheel.visual.BlockEntityVisual;
import dev.golem.core.flywheel.visual.CrumblingCoreResolver;
import dev.golem.core.flywheel.visual.GolemRenderSystem;
import dev.golem.core.flywheel.visual.VisualizationLifecycle;
import dev.golem.core.flywheel.visual.VisualizationManager;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

// Frame dispatcher. Builds the shared per-frame state (modelView with origin offset,
// dynamicTransforms slice) and the backend-agnostic crumbling group list, then hands them to
// the active backend's DrawManager. The actual draw paths (UBO + Mojang RenderPass for
// INSTANCING; SSBO + multidraw + raw GL for INDIRECT) live in the per-backend DrawManager
// implementations — Engine no longer touches GL.
public final class Engine {

    static {
        if (!dev.golem.core.platform.Platform.isClient()) {
            throw new UnsupportedOperationException();
        }
    }

    private static final Matrix4f CAMERA_MODEL_VIEW = new Matrix4f();
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Vector3f CAMERA_POS_RO = new Vector3f();
    private static final Vector4f COLOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);

    private Engine() {
    }

    // What LevelRenderer is handed as its model-view is CameraRenderState.viewRotationMatrix, which
    // is this. NeoForge's stage event carries it; Fabric's level render events carry no matrix at
    // all, so that wiring reads it back off the camera here.
    public static Matrix4fc cameraModelView() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getViewRotationMatrix(CAMERA_MODEL_VIEW);
    }

    public static void onResourceReload() {
        MeshPool.releaseGpuResources();
    }

    public static void drawOpaque(Matrix4fc modelView) {
        drawStage(modelView, Material.Stage.OPAQUE);
    }

    // Normal (Fancy/Fast) transparency path: AfterWeather fires inside the weather framegraph
    // pass, after main/clouds/weather. Main's depth at this point holds terrain + water/glass
    // (translucent terrain writes depth) + clouds, so our particles depth-test correctly
    // against everything. Vanilla translucent particles already drew at AfterTranslucentParticles
    // and didn't write depth, so we always overdraw them — fine, our particles are the focal
    // effect by design. Trade-off: weather (rain/snow) drew before us at AfterWeather, so Golem
    // composites on top — acceptable for explosion/fog clusters.
    public static void drawTranslucent(Matrix4fc modelView) {
        if (Minecraft.useShaderTransparency()) return;
        drawStage(modelView, Material.Stage.TRANSLUCENT);
    }

    // Fabulous transparency path: each translucent layer (translucent blocks, item-entities,
    // particles, clouds, weather) renders into its own framebuffer with its own depth. The
    // post-chain (assets/minecraft/post_effect/transparency.json) reads all layer color+depth
    // textures and per-pixel-sorts them into main. Drawing into main at AfterWeather/AfterLevel
    // gets us either overwritten (the post-chain's blit clobbers our color) or sorted incorrectly
    // (main's depth doesn't include water — we'd draw on top of water regardless of distance).
    //
    // Correct fix: render into the translucent layer target during AfterTranslucentBlocks. At
    // that point vanilla just finished drawing translucent terrain (water/glass) into the
    // translucent target — its depth has terrain (copied from main earlier) + water + glass.
    // Our depth-test correctly z-fails behind water in the same target. Cross-layer sort uses
    // the translucent layer's depth as proxy, same approximation vanilla makes for its own
    // particles.
    public static void drawTranslucentFabulous(Matrix4fc modelView) {
        if (!Minecraft.useShaderTransparency()) return;
        drawStage(modelView, Material.Stage.TRANSLUCENT);
    }

    private static void drawStage(Matrix4fc modelView, Material.Stage stage) {
        VisualizationManager vm = VisualizationLifecycle.current();
        if (vm == null) return;
        DrawManager dm = vm.drawManager();
        GolemRenderSystem.awaitFrame();
        dm.flushAll();
        Vec3i origin = GolemRenderSystem.currentRenderOrigin();
        vm.lightBuffer().update(vm, origin.getX(), origin.getY(), origin.getZ());
        FrameState state = buildFrameState(modelView, vm);
        dm.drawStage(stage, state);
    }

    public static void drawCrumbling(Matrix4fc modelView, LevelRenderState levelState) {
        VisualizationManager vm = VisualizationLifecycle.current();
        if (vm == null) return;
        List<BlockBreakingRenderState> breakingStates = levelState.blockBreakingRenderStates;
        if (breakingStates.isEmpty()) return;

        // Group by (meshHandle, progress). A multiblock's dummies point at a separate core block
        // that owns the BE — the mined block is usually one of the 8 dummies, so a direct lookup
        // misses; fall back to whatever core resolver the content layer installed.
        Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<List<Instance>>> groupTree = new Int2ObjectOpenHashMap<>();
        for (BlockBreakingRenderState breakState : breakingStates) {
            BlockPos pos = breakState.blockPos();
            BlockEntityVisual<?> visual = vm.blockEntityVisual(pos);
            CrumblingCoreResolver resolver = Flywheel.crumblingCoreResolver();
            if (visual == null && resolver != null) {
                BlockPos core = resolver.coreOf(vm.level(), pos, breakState.blockState());
                if (core != null) visual = vm.blockEntityVisual(core);
            }
            if (visual == null) continue;
            int progress = breakState.progress();
            visual.collectCrumblingInstances(instance -> {
                if (instance == null) return;
                int meshHandle = instance.meshHandle();
                groupTree.computeIfAbsent(meshHandle, k -> new Int2ObjectOpenHashMap<>())
                        .computeIfAbsent(progress, k -> new ArrayList<>())
                        .add(instance);
            });
        }
        if (groupTree.isEmpty()) return;

        List<CrumblingDraw> draws = new ArrayList<>();
        for (var meshEntry : groupTree.int2ObjectEntrySet()) {
            int meshHandle = meshEntry.getIntKey();
            for (var progressEntry : meshEntry.getValue().int2ObjectEntrySet()) {
                List<Instance> instances = progressEntry.getValue();
                if (instances.isEmpty()) continue;
                draws.add(new CrumblingDraw(meshHandle, progressEntry.getIntKey(), instances));
            }
        }
        if (draws.isEmpty()) return;

        DrawManager dm = vm.drawManager();
        GolemRenderSystem.awaitFrame();
        dm.flushAll();
        FrameState state = buildFrameState(modelView, vm);
        dm.drawCrumbling(draws, state);
    }

    // The model-view handed in is camera-rotation only (LevelRenderer.java:582-3 builds it from
    // camera.getViewRotationMatrix, which is where the Fabric wiring reads it). Compose Translate(renderOrigin -
    // cameraPos): per-instance matrices are stored relative to the section-aligned render
    // origin, so the float multiply stays in single-precision range even when the world
    // coordinate is large. Subtract as doubles before casting so the cancellation happens
    // at full double precision.
    private static FrameState buildFrameState(Matrix4fc modelView, VisualizationManager vm) {
        Minecraft minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();
        Vec3i origin = GolemRenderSystem.currentRenderOrigin();
        float dx = (float) (origin.getX() - cameraPos.x);
        float dy = (float) (origin.getY() - cameraPos.y);
        float dz = (float) (origin.getZ() - cameraPos.z);
        MODEL_VIEW.set(modelView).translate(dx, dy, dz);
        // ProjMat * cameraRotation * Translate(renderOrigin - cameraPos). camera's
        // getViewRotationProjectionMatrix returns ProjMat * cameraRotation; appending the
        // origin translate gives the matrix the INDIRECT cull pass needs to extract render-
        // origin-space frustum planes.
        camera.getViewRotationProjectionMatrix(VIEW_PROJECTION).translate(dx, dy, dz);
        // cameraPosRO = cameraPos - renderOrigin. dx/dy/dz above is its negation; flip the
        // sign here. Subtraction happens at full double precision before the cast.
        CAMERA_POS_RO.set(-dx, -dy, -dz);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(MODEL_VIEW, COLOR, MODEL_OFFSET, TEXTURE_MATRIX);
        GpuBufferSlice shaderLights = vm.lightBuffer().slice();
        return new FrameState(minecraft, MODEL_VIEW, VIEW_PROJECTION, CAMERA_POS_RO, transforms, shaderLights);
    }
}
//?}
