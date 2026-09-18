package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import dev.golem.core.GolemCore;
import dev.golem.core.flywheel.Instancer;
import dev.golem.core.flywheel.Material;
import dev.golem.core.flywheel.MeshPool;
import dev.golem.core.flywheel.instance.Instance;
import dev.golem.core.render.Billboards;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * The GPU rows behind one billboard visual, whatever supplies them.
 *
 * <p>An entity renderer and a client-owned pool want exactly the same thing — a set of camera-facing
 * quads refreshed every frame, released the moment the source stops claiming them — and differ only
 * in what they are keyed by. Keeping the instancing here means the two cannot drift, and that the
 * per-level cap below is counted once across both.
 */
final class BillboardLayers {

    private static final Map<net.minecraft.world.level.Level, Integer> VISUALS_PER_LEVEL = new IdentityHashMap<>();

    private static final int QUAD_MESH = MeshPool.register("golem_billboard_quad", new float[] {
            -1F, -1F, 0F, 1F, 1F, 0F, 0F, 1F,
             1F, -1F, 0F, 0F, 1F, 0F, 0F, 1F,
             1F,  1F, 0F, 0F, 0F, 0F, 0F, 1F,
            -1F,  1F, 0F, 1F, 0F, 0F, 0F, 1F,
    }, 4, new int[] {0, 1, 2, 2, 3, 0}, 6);

    private final VisualizationContext context;
    private final LayerState[] layers;
    private final Matrix4f transform = new Matrix4f();
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final Vector3f lockedUp = new Vector3f();
    private boolean healthy = true;
    private boolean reportedFailure;
    private int preparedFrame = Integer.MIN_VALUE;
    private int preparedRows;

    BillboardLayers(VisualizationContext context, List<Billboards.Layer> definitions) {
        this.context = context;
        this.layers = new LayerState[definitions.size()];
        for (int i = 0; i < definitions.size(); i++) {
            Billboards.Layer definition = definitions.get(i);
            Material material = switch (definition.mode()) {
                case DEPTH_WRITING_TRANSLUCENT -> Material.TRANSLUCENT_SORTED_NO_CULL;
                case EMISSIVE_TRANSLUCENT -> Material.TRANSLUCENT_NO_DEPTH_WRITE_NO_CULL;
                case ADDITIVE -> Material.ADDITIVE_NO_CULL;
                case SOURCE_ALPHA_ADDITIVE -> Material.SOURCE_ALPHA_ADDITIVE_NO_CULL;
            };
            layers[i] = new LayerState(dev.golem.core.Ids.parse(definition.texture()), material,
                    definition.align(), definition.uv());
        }
        synchronized (VISUALS_PER_LEVEL) {
            VISUALS_PER_LEVEL.merge(context.level(), 1, Integer::sum);
        }
    }

    /** Writes this frame's rows. {@code name} only ever appears in the failure log. */
    void beginFrame(DynamicVisual.Context frame, Billboards.Rows rows, Supplier<Object> name) {
        preparedFrame = frame.frameId();
        preparedRows = 0;
        for (LayerState layer : layers) layer.rows = 0;
        if (!healthy) return;
        synchronized (VISUALS_PER_LEVEL) {
            if (context.drawManager() instanceof dev.golem.core.flywheel.instance.InstancedDrawManager
                    && VISUALS_PER_LEVEL.getOrDefault(context.level(), 0) > 1) {
                clear();
                return;
            }
        }
        try {
            Instancer[] instancers = new Instancer[layers.length];
            for (int i = 0; i < layers.length; i++) {
                LayerState layer = layers[i];
                if (!context.drawManager().canDraw(layer.material)) {
                    clear();
                    return;
                }
                instancers[i] = context.drawManager().instancer(QUAD_MESH, layer.material, layer.texture);
            }
            Quaternionfc cameraOrientation = frame.camera().rotation();
            cameraOrientation.transform(1F, 0F, 0F, right);
            cameraOrientation.transform(0F, 1F, 0F, up);
            lockedUp.set(up.x, 1F, up.z);
            var origin = GolemRenderSystem.currentRenderOrigin();
            rows.write(new Billboards.Frame(frame.partialTick(), frame.camera().position(), cameraOrientation),
                    layerIndex -> {
                if (layerIndex < 0 || layerIndex >= layers.length) {
                    throw new IndexOutOfBoundsException("Unknown billboard layer " + layerIndex);
                }
                LayerState layer = layers[layerIndex];
                Instancer instancer = instancers[layerIndex];
                return new Billboards.RowWriter() {
                    @Override
                    public void row(double x, double y, double z, float halfSize,
                                    float red, float green, float blue, float alpha, int packedLight) {
                        emit(x, y, z, halfSize, red, green, blue, alpha, packedLight, 0F, 0F, 1F, 1F);
                    }

                    @Override
                    public void sprite(double x, double y, double z, float halfSize,
                                       float red, float green, float blue, float alpha, int packedLight,
                                       int column, int row, int columns, int rows) {
                        float scaleU = 1F / columns;
                        float scaleV = 1F / rows;
                        emit(x, y, z, halfSize, red, green, blue, alpha, packedLight,
                                column * scaleU, row * scaleV, scaleU, scaleV);
                    }

                    private void emit(double x, double y, double z, float halfSize,
                                      float red, float green, float blue, float alpha, int packedLight,
                                      float offsetU, float offsetV, float scaleU, float scaleV) {
                        int index = layer.rows++;
                        while (layer.instances.size() <= index) layer.instances.add(instancer.allocate());
                        Instance instance = layer.instances.get(index);
                        Vector3f vertical = layer.align == Billboards.Align.Y_LOCKED ? lockedUp : up;
                        transform.set(
                                right.x * halfSize, right.y * halfSize, right.z * halfSize, 0F,
                                vertical.x * halfSize, vertical.y * halfSize, vertical.z * halfSize, 0F,
                                0F, 1F, 0F, 0F,
                                (float) (x - origin.getX()), (float) (y - origin.getY()),
                                (float) (z - origin.getZ()), 1F);
                        instance.setTransform(transform);
                        instance.setRawColor(red, green, blue, alpha);
                        instance.setLight(packedLight);
                        instance.setOverlay(OverlayTexture.NO_OVERLAY);
                        if (layer.uv == Billboards.Uv.ROTATED_180) {
                            instance.setUV(offsetU + scaleU, offsetV + scaleV, -scaleU, -scaleV);
                        } else {
                            instance.setUV(offsetU, offsetV, scaleU, scaleV);
                        }
                    }
                };
            });
            for (LayerState layer : layers) {
                while (layer.instances.size() > layer.rows) {
                    layer.instances.remove(layer.instances.size() - 1).delete();
                }
                preparedRows += layer.rows;
            }
        } catch (RuntimeException failure) {
            healthy = false;
            clear();
            if (!reportedFailure) {
                reportedFailure = true;
                GolemCore.LOG.error("Billboard visual failed for {}; returning ownership to vanilla",
                        name.get(), failure);
            }
        }
    }

    boolean ownsCurrentFrame() {
        return healthy && preparedRows > 0 && preparedFrame == GolemRenderSystem.currentFrameId();
    }

    void delete() {
        clear();
        synchronized (VISUALS_PER_LEVEL) {
            VISUALS_PER_LEVEL.computeIfPresent(context.level(),
                    (level, count) -> count == 1 ? null : count - 1);
        }
    }

    void clear() {
        for (LayerState layer : layers) {
            for (Instance instance : layer.instances) instance.delete();
            layer.instances.clear();
            layer.rows = 0;
        }
    }

    private static final class LayerState {
        private final Identifier texture;
        private final Material material;
        private final Billboards.Align align;
        private final Billboards.Uv uv;
        private final List<Instance> instances = new ArrayList<>();
        private int rows;

        private LayerState(Identifier texture, Material material, Billboards.Align align, Billboards.Uv uv) {
            this.texture = texture;
            this.material = material;
            this.align = align;
            this.uv = uv;
        }
    }
}
//?}
