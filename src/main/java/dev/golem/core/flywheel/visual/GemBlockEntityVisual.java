package dev.golem.core.flywheel.visual;

//? if >=26.1 {

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

    /**
     * Every live gem visual, so the engine can be asked what it is actually holding.
     *
     * <p>An instanced draw that works looks exactly like a vanilla draw that works, so without a count
     * there is no way to tell the two apart from inside the game — which is how this engine ran for a
     * whole release cycle with no block entity ever reaching it and nothing saying so.
     */
    private static final java.util.Set<GemBlockEntityVisual<?, ?>> LIVE =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private static volatile boolean announced;

    /** Whether a declared model has ever reached the GPU on this client. */
    public static boolean announced() {
        return announced;
    }

    /**
     * Says once, per session, that a declared model actually reached the GPU.
     *
     * <p>The engine is otherwise entirely silent when it works, which makes "it is instancing" and "it
     * received nothing and every machine is drawing the slow way" indistinguishable from a log — and
     * the second of those is what had been happening. One line at the moment the first gem model is
     * written is the cheapest thing that tells them apart afterwards, from a report rather than a
     * running game.
     */
    private static void announce() {
        announced = true;
        int[] census = census();
        var backend = dev.golem.core.flywheel.backend.BackendManager.selected();
        GolemCore.LOG.info("Gem instancing live on backend {}: {} visuals, {} instances",
                backend == null ? "unknown" : backend.name(), census[0], census[2]);
    }

    /**
     * Live visuals, of them healthy ones, GPU rows written, visuals owning the current frame, and
     * visuals the engine treats as animated.
     *
     * <p>Animated means the declaration reads its render state somewhere, so {@code Model.rigid()} is
     * false and {@code beginFrame} rewrites its transforms every frame instead of writing once and
     * leaving them. Moving is the stronger claim that those rewritten transforms actually differed
     * from last frame's. A model that is animated but never moving is frozen on screen and correct
     * everywhere else, which is the failure no screenshot and no exception will ever show.
     */
    public static int[] census() {
        int frame = GolemRenderSystem.currentFrameId();
        int visuals = 0;
        int healthy = 0;
        int instances = 0;
        int owning = 0;
        int animated = 0;
        int moving = 0;
        for (GemBlockEntityVisual<?, ?> visual : LIVE) {
            visuals++;
            if (visual.healthy) healthy++;
            instances += visual.liveSlots;
            if (visual.ownsFrame(frame)) owning++;
            if (!visual.model.rigid()) animated++;
            if (visual.moving) moving++;
        }
        return new int[] {visuals, healthy, instances, owning, animated, moving};
    }

    private final VisualizationContext context;
    private final GemBER<T, S> renderer;
    private final Gem.Model<S> model;
    private final S state;
    private final boolean channelsFixed;
    private final List<Slot> slots = new ArrayList<>();
    private final Matrix4f scratch = new Matrix4f();

    /**
     * The world transform this walk last gave each part, in visit order, and whether it drew.
     *
     * <p>A part is numbered rather than looked up because the tree is sealed: part <i>n</i> is the
     * same part every frame, exactly as the slot list already relies on. Joints are numbered too —
     * they carry no instance, but a joint is the most common thing a whole still subtree hangs from,
     * so the walk has to be able to prove <em>its</em> transform unchanged to skip what is under it.
     */
    private final Matrix4f[] poses;
    private final boolean[] drawn;

    /**
     * The slot each part started at last walk.
     *
     * <p>Slots are positional, so a part earlier in the walk that stops drawing — a mesh chooser is
     * allowed to hand back nothing — shifts every slot after it by one. A subtree proved unchanged is
     * still holding the right values, but no longer in the right slots, so the cursor has to agree
     * before stepping over it.
     */
    private final int[] slotAt;
    private int walked;
    private boolean healthy = true;
    private boolean reportedFailure;
    private boolean written;

    /**
     * Whether {@link #poses} describes the frame that is actually on the GPU.
     *
     * <p>Cleared by anything that makes last frame's answer no longer comparable to this one: the
     * light moving, the render origin moving, or the instances being dropped. While it is false the
     * walk visits every part and compares nothing, which is what makes it safe to trust afterwards.
     */
    private boolean primed;
    private Vec3i writtenOrigin;
    private int writtenLight;
    private int preparedFrame = Integer.MIN_VALUE;
    private volatile int liveSlots;
    private boolean touched;
    private volatile boolean moving;

    private GemBlockEntityVisual(VisualizationContext context, T blockEntity, GemBER<T, S> renderer) {
        super(blockEntity);
        this.context = context;
        this.renderer = renderer;
        this.model = renderer.gem();
        this.state = renderer.createRenderState();
        this.channelsFixed = this.model.channelsFixed();
        int nodes = 0;
        for (Gem.Part<S> root : this.model.roots()) nodes += root.subtreeNodes();
        this.poses = new Matrix4f[nodes];
        this.drawn = new boolean[nodes];
        this.slotAt = new int[nodes];
        for (int i = 0; i < nodes; i++) this.poses[i] = new Matrix4f();
        LIVE.add(this);
    }

    public static <T extends BlockEntity, S extends CompatRenderState> void register(
            BlockEntityType<T> type, Supplier<? extends GemBER<T, S>> renderer) {
        VisualizerRegistry.register(type, (context, blockEntity, partialTick) ->
                new GemBlockEntityVisual<>(context, blockEntity, renderer.get()));
    }

    /** Whether this visual drew the machine this frame, so its vanilla renderer must not draw it too. */
    public boolean ownsCurrentFrame() {
        return ownsFrame(GolemRenderSystem.currentFrameId());
    }

    private boolean ownsFrame(int frame) {
        return healthy && written && preparedFrame == frame;
    }

    @Override
    public void beginFrame(DynamicVisual.Context frame) {
        if (!healthy) return;
        try {
            // An instance holds its transform relative to the render origin, and Engine composes the
            // matching Translate(renderOrigin - cameraPos) into the model-view. So the origin moving
            // — which it does whenever the camera crosses a section boundary — invalidates every
            // transform already written, however fixed the part that wrote it.
            Vec3i origin = frame.renderOrigin();
            if (!origin.equals(writtenOrigin)) primed = false;

            state.blockPos = pos;
            int sampled = Renderers.lightAt(context.level(), pos);
            if (sampled != writtenLight) primed = false;
            state.lightCoords = sampled;

            if (written && primed && model.rigid()) {
                moving = false;
                preparedFrame = frame.frameId();
                return;
            }
            renderer.extract(blockEntity, state, frame.partialTick(), frame.camera().position());

            scratch.identity().translate(pos.getX() - origin.getX(), pos.getY() - origin.getY(),
                    pos.getZ() - origin.getZ());
            boolean comparable = primed;
            touched = false;
            walked = 0;
            int next = 0;
            for (Gem.Part<S> root : model.roots()) next = write(root, scratch, next, true, comparable);
            while (slots.size() > next) slots.remove(slots.size() - 1).instance.delete();

            // Whether anything this model draws actually moved since the last frame. A model that is
            // animated but frozen writes the same transforms forever and looks exactly like one that
            // is working, so the comparison is the only thing that tells them apart. It is now the
            // same comparison that decides whether to write at all, so it can no longer disagree
            // with what the GPU was sent.
            moving = comparable && touched;
            primed = true;
            writtenOrigin = origin;
            writtenLight = sampled;
            written = next > 0;
            liveSlots = slots.size();
            preparedFrame = frame.frameId();
            if (written && !announced) announce();
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
    private int write(Gem.Part<S> part, Matrix4f parent, int slot, boolean parentVisible,
                      boolean parentUnchanged) {
        int node = walked++;

        // Nothing above this part moved, nothing in it or under it reads the render state, and the
        // model-wide channels every part falls back to are fixed too — so every transform, tint,
        // light and visibility in this subtree is already on the GPU with the value it would be
        // given again. Stepping over it is the whole point of declaring a model rather than drawing
        // one: a spinning blade costs a blade, not the machine it is bolted to.
        if (parentUnchanged && channelsFixed && slot == slotAt[node] && part.rigidTree()) {
            walked = node + part.subtreeNodes();
            dev.golem.core.flywheel.DrawStats.skipped(part.subtreeNodes());
            return slot + part.subtreeSlots();
        }
        slotAt[node] = slot;

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
            apply(target, here, part.lightOf(state, model.lightOf(state, state.lightCoords)),
                    part.tintOf(state), visible);
            slot++;
        }

        for (Gem.Part<S> child : part.children()) slot = write(child, here, slot, visible, unchanged);
        return slot;
    }

    /**
     * Sends one part's properties to its instance, and only the ones that differ.
     *
     * <p>Every write marks a byte range dirty, and the backend uploads what is marked — so writing a
     * value it already holds costs an upload to change nothing. Most machines in a loaded world are
     * in this position on most frames: they read their facing from the render state, which makes them
     * animated as far as the declaration is concerned, and then never turn.
     *
     * <p>A slot fresh from {@code allocate} has defined contents but not <em>these</em> contents, so
     * the first write after one is unconditional and in the order the instancer documents — colour
     * before visibility, because an invisible slot keeps its colour in the cache rather than the slab.
     */
    private void apply(Slot target, Matrix4f transform, int light, int tint, boolean visible) {
        if (!target.written) {
            target.written = true;
            target.transform.set(transform);
            target.light = light;
            target.color = tint;
            target.visible = visible;
            target.instance.setTransform(transform);
            target.instance.setLight(light);
            target.instance.setOverlay(OverlayTexture.NO_OVERLAY);
            target.instance.setColor(tint);
            target.instance.setVisible(visible);
            touched = true;
            dev.golem.core.flywheel.DrawStats.wrote();
            return;
        }
        if (!target.transform.equals(transform)) {
            target.transform.set(transform);
            target.instance.setTransform(transform);
            touched = true;
            dev.golem.core.flywheel.DrawStats.wrote();
        }
        if (target.light != light) {
            target.light = light;
            target.instance.setLight(light);
        }
        if (target.color != tint) {
            target.color = tint;
            target.instance.setColor(tint);
        }
        if (target.visible != visible) {
            target.visible = visible;
            target.instance.setVisible(visible);
        }
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
            case PLASMA_GLOW -> Material.SOURCE_ALPHA_ADDITIVE_NO_CULL;
        };
    }

    @Override
    public void updateLight(long sectionKey) {
        written = false;
        primed = false;
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
        LIVE.remove(this);
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
        liveSlots = 0;
        written = false;
        primed = false;
        preparedFrame = Integer.MIN_VALUE;
    }

    /**
     * One instance, the declaration it was allocated against, and the last values sent to it.
     *
     * <p>The cache is what makes a redundant write detectable at all. The instancer keeps copies of
     * its own — it has to, for the rule that an invisible slot holds its colour in the cache rather
     * than the slab — but they are private to it and it has no way to answer "is this what you
     * already hold".
     */
    private static final class Slot {

        private final Instance instance;
        private final Geometry geometry;
        private final Surface surface;
        private final Matrix4f transform = new Matrix4f();
        private boolean written;
        private int light;
        private int color;
        private boolean visible;

        private Slot(Instance instance, Geometry geometry, Surface surface) {
            this.instance = instance;
            this.geometry = geometry;
            this.surface = surface;
        }
    }

    private record Uploaded(Geometry geometry, int handle) {}
}
//?}
