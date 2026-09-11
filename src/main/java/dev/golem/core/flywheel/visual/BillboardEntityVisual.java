package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

import dev.golem.core.GolemCore;
import dev.golem.core.flywheel.Instancer;
import dev.golem.core.flywheel.Material;
import dev.golem.core.flywheel.MeshPool;
import dev.golem.core.flywheel.instance.Instance;
import dev.golem.core.render.Billboards;
import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** Engine adapter for billboard rows synchronously supplied by an entity renderer. */
public final class BillboardEntityVisual<T extends Entity> extends EntityVisual<T>
        implements SynchronousDynamicVisual {

    private static final Map<net.minecraft.world.level.Level, Integer> VISUALS_PER_LEVEL = new IdentityHashMap<>();

    private static final int QUAD_MESH = MeshPool.register("golem_billboard_quad", new float[] {
            -1F, -1F, 0F, 1F, 1F, 0F, 0F, 1F,
             1F, -1F, 0F, 0F, 1F, 0F, 0F, 1F,
             1F,  1F, 0F, 0F, 0F, 0F, 0F, 1F,
            -1F,  1F, 0F, 1F, 0F, 0F, 0F, 1F,
    }, 4, new int[] {0, 1, 2, 2, 3, 0}, 6);

    private final VisualizationContext context;
    private final Billboards.LayeredSource<T> source;
    private final LayerState[] layers;
    private final Matrix4f transform = new Matrix4f();
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();
    private boolean healthy = true;
    private boolean reportedFailure;
    private int preparedFrame = Integer.MIN_VALUE;
    private int preparedRows;

    private BillboardEntityVisual(VisualizationContext context, T entity, List<Billboards.Layer> definitions,
                                   Billboards.LayeredSource<T> source) {
        super(entity);
        this.context = context;
        this.source = source;
        this.layers = new LayerState[definitions.size()];
        for (int i = 0; i < definitions.size(); i++) {
            Billboards.Layer definition = definitions.get(i);
            Material material = switch (definition.mode()) {
                case DEPTH_WRITING_TRANSLUCENT -> Material.TRANSLUCENT_SORTED_NO_CULL;
                case ADDITIVE -> Material.ADDITIVE_NO_CULL;
                case SOURCE_ALPHA_ADDITIVE -> Material.SOURCE_ALPHA_ADDITIVE_NO_CULL;
            };
            layers[i] = new LayerState(dev.golem.core.Ids.parse(definition.texture()), material);
        }
        synchronized (VISUALS_PER_LEVEL) {
            VISUALS_PER_LEVEL.merge(context.level(), 1, Integer::sum);
        }
    }

    public static <T extends Entity> void register(EntityType<T> type, List<Billboards.Layer> layers,
                                                    Billboards.LayeredSource<T> source) {
        VisualizerRegistry.register(type, (context, entity, partialTick) ->
                new BillboardEntityVisual<>(context, entity, layers, source));
    }

    @Override
    public void beginFrame(DynamicVisual.Context frame) {
        preparedFrame = frame.frameId();
        preparedRows = 0;
        for (LayerState layer : layers) layer.rows = 0;
        if (!healthy) return;
        synchronized (VISUALS_PER_LEVEL) {
            if (context.drawManager() instanceof dev.golem.core.flywheel.instance.InstancedDrawManager
                    && VISUALS_PER_LEVEL.getOrDefault(context.level(), 0) > 1) {
                clearInstances();
                return;
            }
        }
        try {
            Instancer[] instancers = new Instancer[layers.length];
            for (int i = 0; i < layers.length; i++) {
                LayerState layer = layers[i];
                if (!context.drawManager().canDraw(layer.material)) {
                    clearInstances();
                    return;
                }
                instancers[i] = context.drawManager().instancer(QUAD_MESH, layer.material, layer.texture);
            }
            Quaternionfc cameraOrientation = frame.camera().rotation();
            cameraOrientation.transform(1F, 0F, 0F, right);
            cameraOrientation.transform(0F, 1F, 0F, up);
            var origin = GolemRenderSystem.currentRenderOrigin();
            source.write(entity, new Billboards.Frame(frame.partialTick(), frame.camera().position(),
                    cameraOrientation), layerIndex -> {
                if (layerIndex < 0 || layerIndex >= layers.length) {
                    throw new IndexOutOfBoundsException("Unknown billboard layer " + layerIndex);
                }
                LayerState layer = layers[layerIndex];
                Instancer instancer = instancers[layerIndex];
                return (x, y, z, halfSize, red, green, blue, alpha, packedLight) -> {
                    int index = layer.rows++;
                    while (layer.instances.size() <= index) layer.instances.add(instancer.allocate());
                    Instance instance = layer.instances.get(index);
                    transform.set(
                            right.x * halfSize, right.y * halfSize, right.z * halfSize, 0F,
                            up.x * halfSize, up.y * halfSize, up.z * halfSize, 0F,
                            0F, 1F, 0F, 0F,
                            (float) (x - origin.getX()), (float) (y - origin.getY()),
                            (float) (z - origin.getZ()), 1F);
                    instance.setTransform(transform);
                    instance.setRawColor(red, green, blue, alpha);
                    instance.setLight(packedLight);
                    instance.setOverlay(OverlayTexture.NO_OVERLAY);
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
            clearInstances();
            if (!reportedFailure) {
                reportedFailure = true;
                GolemCore.LOG.error("Billboard visual failed for {}; returning ownership to vanilla",
                        entity.getType(), failure);
            }
        }
    }

    public boolean ownsCurrentFrame() {
        return healthy && preparedRows > 0 && preparedFrame == GolemRenderSystem.currentFrameId();
    }

    @Override
    public void update(float partialTick) {
    }

    @Override
    public void delete() {
        clearInstances();
        synchronized (VISUALS_PER_LEVEL) {
            VISUALS_PER_LEVEL.computeIfPresent(context.level(),
                    (level, count) -> count == 1 ? null : count - 1);
        }
    }

    private void clearInstances() {
        for (LayerState layer : layers) {
            for (Instance instance : layer.instances) instance.delete();
            layer.instances.clear();
            layer.rows = 0;
        }
    }

    private static final class LayerState {
        private final Identifier texture;
        private final Material material;
        private final List<Instance> instances = new ArrayList<>();
        private int rows;

        private LayerState(Identifier texture, Material material) {
            this.texture = texture;
            this.material = material;
        }
    }
}
//?}
