package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

import dev.golem.core.GolemCore;
import dev.golem.core.Ids;
import dev.golem.core.flywheel.Instancer;
import dev.golem.core.flywheel.Material;
import dev.golem.core.flywheel.MeshPool;
import dev.golem.core.flywheel.instance.Instance;
import dev.golem.core.render.CompatRenderState;
import dev.golem.core.render.Renderers;
import dev.golem.core.render.gem.Gem;
import dev.golem.core.render.gem.GemBER;
import dev.golem.core.render.gem.Geometry;
import dev.golem.core.render.gem.Surface;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.joml.Matrix4f;

/**
 * Engine adapter that draws a {@linkplain Gem declared model} with GPU instancing.
 *
 * <p>It reads the same declaration the vanilla pass reads and the same {@code extract} step the vanilla
 * pass uses, so the two backends cannot disagree about what a machine looks like — that is the whole
 * reason the renderer declares rather than draws. Every geometry lump becomes one resident mesh and
 * every part becomes one persistent {@link Instance}; what changes per frame is only the handful of
 * matrices whose parts read the render state.
 *
 * <p>A model with no state-driven part at all is written once and then left alone until its light
 * changes. Most machines fall just short of that, because their facing is read from the render state
 * like anything else, so their per-frame cost is one extract and a handful of matrix multiplies — still
 * far less than the vanilla path, which re-submits every triangle every frame.
 *
 * <p>Like every other visual here this is an <em>addition</em>. Any failure marks the visual unhealthy,
 * drops its instances and stops claiming frames, which hands the machine straight back to its vanilla
 * renderer rather than leaving a hole in the world.
 */
public final class GemBlockEntityVisual<T extends BlockEntity, S extends CompatRenderState>
        extends BlockEntityVisual<T> implements SynchronousDynamicVisual, LightUpdatedVisual {

    private static final Map<String, Uploaded> MESHES = new HashMap<>();

    private final VisualizationContext context;
    private final GemBER<T, S> renderer;
    private final Gem.Model<S> model;
    private final S state;
    private final List<Slot> slots = new ArrayList<>();
    private final Matrix4f scratch = new Matrix4f();
    private boolean healthy = true;
    private boolean reportedFailure;
    private boolean written;
    private int preparedFrame = Integer.MIN_VALUE;

    private GemBlockEntityVisual(VisualizationContext context, T blockEntity, GemBER<T, S> renderer) {
        super(blockEntity);
        this.context = context;
        this.renderer = renderer;
        this.model = renderer.gem();
        this.state = renderer.createRenderState();
    }

    public static <T extends BlockEntity, S extends CompatRenderState> void register(
            BlockEntityType<T> type, Supplier<? extends GemBER<T, S>> renderer) {
        VisualizerRegistry.register(type, (context, blockEntity, partialTick) ->
                new GemBlockEntityVisual<>(context, blockEntity, renderer.get()));
    }

    /** Whether this visual drew the machine this frame, so its vanilla renderer must not draw it too. */
    public boolean ownsCurrentFrame() {
        return healthy && written && preparedFrame == GolemRenderSystem.currentFrameId();
    }

    @Override
    public void beginFrame(DynamicVisual.Context frame) {
        if (!healthy) return;
        try {
            if (written && model.rigid()) {
                preparedFrame = frame.frameId();
                return;
            }
            state.blockPos = pos;
            state.lightCoords = Renderers.lightAt(context.level(), pos);
            renderer.extract(blockEntity, state, frame.partialTick(), frame.camera().position());

            Vec3i origin = GolemRenderSystem.currentRenderOrigin();
            scratch.identity().translate(pos.getX() - origin.getX(), pos.getY() - origin.getY(),
                    pos.getZ() - origin.getZ());
            int next = 0;
            for (Gem.Part<S> root : model.roots()) next = write(root, scratch, next, true);
            while (slots.size() > next) slots.remove(slots.size() - 1).instance.delete();

            written = next > 0;
            preparedFrame = frame.frameId();
        } catch (RuntimeException failure) {
            fail(failure);
        }
    }

    /**
     * Writes one part and its children, depth first, and returns the next free slot.
     *
     * <p>Slots are keyed by position in that walk rather than by identity, because the walk is over a
     * sealed tree: part <i>n</i> is the same part every frame, so its instance can simply be reused.
     */
    private int write(Gem.Part<S> part, Matrix4f parent, int slot, boolean parentVisible) {
        boolean visible = parentVisible && part.visible(state);
        Matrix4f here = new Matrix4f(parent);
        part.applyTo(here, state);

        Geometry geometry = part.geometryOf(state);
        if (geometry != null) {
            Surface surface = part.surfaceOf(state);
            Slot target;
            if (slot < slots.size()) {
                target = slots.get(slot);
                if (target.geometry != geometry || !target.surface.equals(surface)) {
                    target.instance.delete();
                    target = allocate(geometry, surface);
                    slots.set(slot, target);
                }
            } else {
                target = allocate(geometry, surface);
                slots.add(target);
            }
            target.instance.setTransform(here);
            target.instance.setLight(part.lightOf(state, model.lightOf(state, state.lightCoords)));
            target.instance.setOverlay(OverlayTexture.NO_OVERLAY);
            target.instance.setColor(part.tintOf(state));
            target.instance.setVisible(visible);
            slot++;
        }

        for (Gem.Part<S> child : part.children()) slot = write(child, here, slot, visible);
        return slot;
    }

    /**
     * One instance in the bucket for this (mesh, surface) pair. A part that chooses its mesh or its skin
     * from the render state moves between buckets, which is a delete and a fresh allocation rather than
     * a property on the instance — the bucket is what a draw call is.
     */
    private Slot allocate(Geometry geometry, Surface surface) {
        Instancer instancer = context.drawManager()
                .instancer(mesh(geometry), materialFor(surface), Ids.parse(surface.texture()));
        return new Slot(instancer.allocate(), geometry, surface);
    }

    /**
     * The resident mesh for one lump, uploaded once per distinct name.
     *
     * <p>A resource reload rebuilds the renderers, so the same OBJ comes back as a fresh
     * {@link Geometry} under the same name — with different triangles if a pack replaced the file.
     * Reusing the pool slot and replacing its contents keeps a pack swap honest without leaking a slot
     * on every reload.
     */
    private static int mesh(Geometry geometry) {
        synchronized (MESHES) {
            Uploaded held = MESHES.get(geometry.name());
            if (held != null && held.geometry == geometry) return held.handle;
            if (held != null) {
                MeshPool.replace(held.handle, geometry.vertices(), geometry.vertexCount(),
                        geometry.indices(), geometry.indices().length);
                MESHES.put(geometry.name(), new Uploaded(geometry, held.handle));
                return held.handle;
            }
            int registered = MeshPool.register(geometry.name(), geometry.vertices(), geometry.vertexCount(),
                    geometry.indices(), geometry.indices().length);
            MESHES.put(geometry.name(), new Uploaded(geometry, registered));
            return registered;
        }
    }

    private static Material materialFor(Surface surface) {
        return switch (surface.blend()) {
            case SOLID, CUTOUT, CUTOUT_CULL -> Material.CUTOUT;
            case CUTOUT_NO_CULL -> Material.CUTOUT_NO_CULL;
            case TRANSLUCENT -> Material.TRANSLUCENT;
            case TRANSLUCENT_EMISSIVE -> Material.TRANSLUCENT_NO_DEPTH_WRITE;
        };
    }

    @Override
    public void updateLight(long sectionKey) {
        written = false;
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        if (!healthy) return;
        for (Slot slot : slots) consumer.accept(slot.instance);
    }

    @Override
    public void update(float partialTick) {
    }

    @Override
    public void delete() {
        clearInstances();
    }

    private void fail(RuntimeException failure) {
        healthy = false;
        clearInstances();
        if (!reportedFailure) {
            reportedFailure = true;
            GolemCore.LOG.error("Gem visual failed for {} at {}; returning ownership to vanilla",
                    blockEntity.getType(), pos, failure);
        }
    }

    private void clearInstances() {
        for (Slot slot : slots) slot.instance.delete();
        slots.clear();
        written = false;
        preparedFrame = Integer.MIN_VALUE;
    }

    private record Slot(Instance instance, Geometry geometry, Surface surface) {}

    private record Uploaded(Geometry geometry, int handle) {}
}
//?}
