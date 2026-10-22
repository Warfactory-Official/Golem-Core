package dev.golem.core.gemrender;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.golem.core.GolemCore;
import dev.golem.core.render.CompatRenderState;
import dev.golem.core.render.Renderers;
import dev.golem.core.render.gem.Gem;
import dev.golem.core.render.gem.GemBER;
import dev.golem.core.render.gem.Geometry;
import dev.golem.core.render.gem.Surface;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** A {@linkplain Gem declared model} as Flywheel instances, one per part; any failure hands it back to vanilla. */
final class GemVisual<T extends BlockEntity, S extends CompatRenderState>
        extends AbstractBlockEntityVisual<T> implements SimpleDynamicVisual {

    /** Live visuals by position, for {@code Visuals.owns} and the census. */
    private static final Map<BlockPos, GemVisual<?, ?>> LIVE = new ConcurrentHashMap<>();
    private static volatile boolean announced;
    private static int writes;
    private static int skipped;

    private final GemBER<T, S> renderer;
    private final Gem.Model<S> model;
    private final S state;
    private final boolean channelsFixed;
    private final List<Slot> slots = new ArrayList<>();
    private final Matrix4f scratch = new Matrix4f();

    /** Per part in visit order (joints too): last transform, whether it drew, and its first slot. */
    private final Matrix4f[] poses;
    private final boolean[] drawn;

    private final int[] slotAt;
    private final int[] slotSpan;
    /** Whether the walk had reached a part the vanilla pass must draw ({@code Gem.replayUninstanced}), entering and leaving each node. */
    private final boolean[] reachedAt;
    private final boolean[] reachedAfter;
    private int walked;
    private boolean reached;
    private volatile boolean healthy = true;
    private volatile boolean written;
    private volatile int liveSlots;
    private volatile boolean moving;
    private volatile float reach;
    private boolean hidden;
    private boolean primed;
    private int writtenLight;
    private boolean touched;

    GemVisual(VisualizationContext ctx, T blockEntity, float partialTick, GemBER<T, S> renderer) {
        super(ctx, blockEntity, partialTick);
        this.renderer = renderer;
        this.model = renderer.gem();
        this.state = renderer.createRenderState();
        this.channelsFixed = this.model.channelsFixed();
        int nodes = 0;
        for (Gem.Part<S> root : this.model.roots()) nodes += root.subtreeNodes();
        this.poses = new Matrix4f[nodes];
        this.drawn = new boolean[nodes];
        this.slotAt = new int[nodes];
        this.slotSpan = new int[nodes];
        this.reachedAt = new boolean[nodes];
        this.reachedAfter = new boolean[nodes];
        for (int i = 0; i < nodes; i++) this.poses[i] = new Matrix4f();
        LIVE.put(pos, this);
    }

    /** Whether this position's declared parts are on the GPU, so its vanilla renderer leaves them out. */
    static boolean owns(BlockPos pos) {
        GemVisual<?, ?> visual = LIVE.get(pos);
        return visual != null && visual.healthy && visual.written && visual.drawsAt(camera());
    }

    /**
     * Where vanilla would draw it: within its view distance unless off screen, and on 26.1 short of the render-distance
     * fog, which GemRender's 26.1 fog lacks. From the camera alone, so vanilla and this visual agree each frame.
     */
    private boolean drawsAt(Vec3 camera) {
        Vec3 centre = Vec3.atCenterOf(pos);
        if (!model.isOffScreen() && !centre.closerThan(camera, model.viewDistance())) return false;
        //? if >=26.1 {
        double dx = centre.x - camera.x, dz = centre.z - camera.z;
        double cylindrical = Math.max(Math.sqrt(dx * dx + dz * dz), Math.abs(centre.y - camera.y));
        float end = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16F;
        return cylindrical + reach <= end - Mth.clamp(end / 10F, 4F, 64F);
        //?} else {
        /*return true;
        *///?}
    }

    private static Vec3 camera() {
        //? if >=26.1 {
        return Minecraft.getInstance().gameRenderer.getMainCamera().position();
        //?} else {
        /*return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        *///?}
    }

    /** Visuals, healthy ones, instances, owning ones, animated ones, moving ones, writes, skipped parts. */
    static int[] census() {
        int visuals = 0, healthy = 0, instances = 0, owning = 0, animated = 0, moving = 0;
        for (GemVisual<?, ?> visual : LIVE.values()) {
            visuals++;
            if (visual.healthy) healthy++;
            instances += visual.liveSlots;
            if (visual.healthy && visual.written) owning++;
            if (!visual.model.rigid()) animated++;
            if (visual.moving) moving++;
        }
        return new int[] {visuals, healthy, instances, owning, animated, moving, writes, skipped};
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        if (!healthy) return;
        try {
            state.blockPos = pos;
            int sampled = Renderers.lightAt(level, pos);
            if (sampled != writtenLight) primed = false;
            state.lightCoords = sampled;

            //? if >=26.1 {
            Vec3 camera = ctx.camera().position();
            //?} else {
            /*Vec3 camera = ctx.camera().getPosition();
            *///?}
            if (written && !drawsAt(camera)) {
                hide();
                return;
            }
            if (hidden) {
                hidden = false;
                primed = false;
            }

            // A model with no state-driven part is written once and left alone until its light changes.
            if (written && primed && model.rigid()) {
                moving = false;
                return;
            }

            renderer.extract(blockEntity, state, ctx.partialTick(), camera);

            scratch.identity().translate(visualPos.getX(), visualPos.getY(), visualPos.getZ());
            boolean comparable = primed;
            touched = false;
            walked = 0;
            reached = false;
            int next = 0;
            for (Gem.Part<S> root : model.roots()) next = write(root, scratch, next, true, comparable);
            while (slots.size() > next) slots.remove(slots.size() - 1).instance.delete();

            moving = comparable && touched;
            if (touched) measureReach();
            primed = true;
            writtenLight = sampled;
            liveSlots = slots.size();
            written = next > 0;
            if (written && !announced) announce();
        } catch (RuntimeException failure) {
            fail(failure);
        }
    }

    /** Hides every part until {@link #drawsAt} holds again; the next write sends everything afresh. */
    private void hide() {
        if (hidden) return;
        hidden = true;
        for (Slot slot : slots) {
            slot.instance.setVisible(false);
            slot.instance.setChanged();
            slot.written = false;
        }
    }

    /** The farthest drawn corner from the block centre, kept as the largest seen. */
    private void measureReach() {
        Gem.extents(model, state, new Matrix4f(), corner -> {
            float r = (float) Math.sqrt(Mth.square(corner.x() - 0.5F) + Mth.square(corner.y() - 0.5F)
                    + Mth.square(corner.z() - 0.5F));
            if (r > reach) reach = r;
        });
    }

    /** Writes one part and its children, depth first, and returns the next free slot. */
    private int write(Gem.Part<S> part, Matrix4f parent, int slot, boolean parentVisible,
                      boolean parentUnchanged) {
        int node = walked++;

        // Nothing above moved and nothing in this subtree reads the render state: its instances already hold
        // what they would be given again. A spinning blade costs a blade, not the machine it is bolted to.
        if (parentUnchanged && channelsFixed && slot == slotAt[node] && reached == reachedAt[node]
                && part.rigidTree()) {
            walked = node + part.subtreeNodes();
            skipped += part.subtreeNodes();
            reached = reachedAfter[node];
            return slot + slotSpan[node];
        }
        slotAt[node] = slot;
        reachedAt[node] = reached;

        boolean visible = parentVisible && part.visible(state);
        Matrix4f here = new Matrix4f(parent);
        part.applyTo(here, state);

        Matrix4f cached = poses[node];
        boolean unchanged = parentUnchanged && visible == drawn[node] && here.equals(cached);
        if (!unchanged) {
            cached.set(here);
            drawn[node] = visible;
        }

        Geometry geometry = part.geometryOf(state);
        if (geometry != null && visible && !part.instanced(state)) reached = true;
        if (geometry != null && !reached) {
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
            apply(target, here, part.lightOf(state, model.lightOf(state, state.lightCoords)),
                    part.tintOf(state), visible);
            slot++;
        }

        for (Gem.Part<S> child : part.children()) slot = write(child, here, slot, visible, unchanged);
        slotSpan[node] = slot - slotAt[node];
        reachedAfter[node] = reached;
        return slot;
    }

    /** Sends only the properties that differ: {@code setChanged} queues an upload whether or not anything did. */
    private void apply(Slot target, Matrix4f transform, int light, int tint, boolean visible) {
        TransformedInstance instance = target.instance;
        if (!target.written) {
            target.written = true;
            target.transform.set(transform);
            target.light = light;
            target.color = tint;
            target.visible = visible;
            instance.setTransform(transform);
            instance.light(light);
            instance.overlay(OverlayTexture.NO_OVERLAY);
            instance.colorArgb(tint);
            instance.setVisible(visible);
            instance.setChanged();
            touched = true;
            writes++;
            return;
        }

        boolean changed = false;
        if (!target.transform.equals(transform)) {
            target.transform.set(transform);
            instance.setTransform(transform);
            changed = true;
            touched = true;
            writes++;
        }
        if (target.light != light) {
            target.light = light;
            instance.light(light);
            changed = true;
        }
        if (target.color != tint) {
            target.color = tint;
            instance.colorArgb(tint);
            changed = true;
        }
        if (target.visible != visible) {
            target.visible = visible;
            instance.setVisible(visible);
            changed = true;
        }
        if (changed) instance.setChanged();
    }

    /** One instance in the (mesh, surface) bucket. Switching mesh or skin moves the part to another bucket. */
    private Slot allocate(Geometry geometry, Surface surface) {
        Instancer<TransformedInstance> instancer = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, GemModels.of(geometry, surface));
        return new Slot(instancer.createInstance(), geometry, surface);
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        if (!healthy) return;
        for (Slot slot : slots) consumer.accept(slot.instance);
    }

    @Override
    public void updateLight(float partialTick) {
        written = false;
        primed = false;
    }

    @Override
    protected void _delete() {
        LIVE.remove(pos, this);
        clearInstances();
    }

    private void fail(RuntimeException failure) {
        healthy = false;
        clearInstances();
        GolemCore.LOG.error("Gem visual failed for {} at {}; returning ownership to vanilla",
                blockEntity.getType(), pos, failure);
    }

    private void clearInstances() {
        for (Slot slot : slots) slot.instance.delete();
        slots.clear();
        liveSlots = 0;
        written = false;
        primed = false;
    }

    /** One line when the first model reaches the GPU, so a working engine is visible in the log. */
    private static void announce() {
        announced = true;
        int[] census = census();
        GolemCore.LOG.info("Gem instancing live on GemRender backend {}: {} visuals, {} instances",
                GemRender.backendName(), census[0], census[2]);
    }

    private static final class Slot {

        private final TransformedInstance instance;
        private final Geometry geometry;
        private final Surface surface;
        private final Matrix4f transform = new Matrix4f();
        private boolean written;
        private int light;
        private int color;
        private boolean visible;

        private Slot(TransformedInstance instance, Geometry geometry, Surface surface) {
            this.instance = instance;
            this.geometry = geometry;
            this.surface = surface;
        }
    }
}
