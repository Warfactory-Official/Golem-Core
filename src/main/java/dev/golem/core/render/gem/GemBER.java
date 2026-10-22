package dev.golem.core.render.gem;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.golem.core.render.CameraInfo;
import dev.golem.core.render.CompatBER;
import dev.golem.core.platform.Platform;
import dev.golem.core.render.CompatRenderState;
import dev.golem.core.world.SubLevels;
import dev.golem.core.render.Frustums;
import dev.golem.core.render.SubmitSink;
import dev.golem.core.render.gem.Gem.Model;
import dev.golem.core.render.Visuals;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * A block-entity renderer that declares its model instead of drawing it.
 *
 * <p>Subclasses implement two things: {@link #setup} states the model once, and {@link #extract} reads
 * the block entity into the render state the model's channels bind to. {@link #submit} is final and
 * replays the declaration; a renderer with its own submit body could only feed the vanilla pass, and the
 * instanced backend needs the model as data it can allocate against before the first frame.
 *
 * <p>The model is built on first use, not in the constructor: its parts name loaded meshes, and a subclass
 * field holding one is still null while a superclass constructor runs, so calling {@code setup} there would
 * hand it a half-built renderer. First use is on the render thread, after resources are loaded, once.
 *
 * <p>{@link #viewDistance} and {@link #shouldRenderOffScreen} come from the model, so a machine states how
 * far it draws where it states what it draws.
 */
public abstract class GemBER<T extends BlockEntity, S extends CompatRenderState> implements CompatBER<T, S> {

    private @Nullable Model<S> model;

    protected GemBER() {}

    /** States this renderer's model. Runs once, on the render thread, before the first frame. */
    protected abstract void setup(Model<S> model);

    /** The declared model, built on first use. */
    public final Model<S> gem() {
        Model<S> built = model;
        if (built == null) {
            built = Gem.model();
            setup(built);
            built.seal();
            model = built;
        }
        return built;
    }

    @Override
    public final void submit(S state, PoseStack poseStack, SubmitSink sink, CameraInfo camera) {
        boolean owned = Visuals.owns(state.blockPos);
        if (!owned) {
            Gem.replay(gem(), state, poseStack, sink, state.lightCoords);
            if (cullable()) learnReach(state);
        } else {
            Gem.replayUninstanced(gem(), state, poseStack, sink, state.lightCoords);
        }
        if (tracing) trace(state.blockPos, owned);
        if (undeclared == null) {
            undeclaredBase = false;
            submitUndeclared(state, poseStack, sink, camera);
            undeclared = !undeclaredBase;
        } else {
            submitUndeclared(state, poseStack, sink, camera);
        }
    }

    /**
     * Per-position count of submits that replayed the declaration and submits that left it to an
     * instanced visual, while {@link #trace(boolean)} is on.
     *
     * <p>An instanced frame and a vanilla frame are the same pixels, so a screenshot cannot say which path
     * drew a machine; a fallback test needs this count. Off by default: it costs a map write per machine
     * per frame.
     */
    private static volatile boolean tracing;
    private static final Map<Long, int[]> TRACE = new ConcurrentHashMap<>();

    /** Turns replay tracing on or off; turning it on starts every count from zero. */
    public static void trace(boolean on) {
        TRACE.clear();
        tracing = on;
    }

    /** {@code {replayed, skipped}} submits at {@code pos} since tracing was switched on. */
    public static int[] traced(net.minecraft.core.BlockPos pos) {
        int[] counts = TRACE.get(pos.asLong());
        if (counts == null) return new int[2];
        synchronized (counts) {
            return counts.clone();
        }
    }

    /** Every position traced since tracing was switched on. */
    public static List<net.minecraft.core.BlockPos> tracedPositions() {
        List<net.minecraft.core.BlockPos> out = new ArrayList<>();
        for (Long packed : TRACE.keySet()) out.add(net.minecraft.core.BlockPos.of(packed));
        return out;
    }

    private static void trace(net.minecraft.core.@Nullable BlockPos pos, boolean owned) {
        if (pos == null) return;
        int[] counts = TRACE.computeIfAbsent(pos.asLong(), key -> new int[2]);
        synchronized (counts) {
            counts[owned ? 1 : 0]++;
        }
    }

    /**
     * Geometry a machine <em>builds</em> rather than places: a beam whose vertices come from where it
     * lands, a flame that billboards to the camera, a fluid surface whose shape is its fill level.
     *
     * <p>The instanced backend has no resident mesh for these, so this runs through the vanilla pass on every
     * node in every frame, including frames where the instanced backend owns the declared tree. Override it
     * only for geometry with no fixed shape; a mesh placed by a computed transform belongs in {@link #setup}.
     */
    protected void submitUndeclared(S state, PoseStack poseStack, SubmitSink sink, CameraInfo camera) {
        undeclaredBase = true;
    }

    @Override
    public int viewDistance() {
        return gem().viewDistance();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return gem().isOffScreen();
    }

    /**
     * A model that draws outside its own block stays in the level's globally-rendered set, which is never
     * distance-culled: culling it by its one owning cell made multiblock machines vanish when that cell
     * scrolled off. It is frustum-culled by the box its own parts were last drawn in.
     */
    @Override
    public boolean shouldRender(T blockEntity, Vec3 cameraPosition) {
        BlockPos pos = blockEntity.getBlockPos();
        // On a sub-level the block sits in a far plot while it is seen elsewhere: measure in the world, never frustum-cull.
        boolean carried = blockEntity.getLevel() != null && SubLevels.isOnSubLevel(blockEntity.getLevel(), pos);
        if (!gem().isOffScreen()) {
            Vec3 centre = Vec3.atCenterOf(pos);
            double range = viewDistance();
            return carried ? SubLevels.distanceSq(blockEntity.getLevel(), centre, cameraPosition) < range * range
                    : centre.closerThan(cameraPosition, range);
        }
        if (!cullable() || carried) return true;
        Reach seen = reach.computeIfAbsent(pos.asLong(), key -> new Reach());
        seen.attach(blockEntity);
        if (!seen.known) return true;
        float[] box = seen.box;
        if (Frustums.isVisible(new AABB(
                pos.getX() + box[0] - REACH_PAD, pos.getY() + box[1] - REACH_PAD, pos.getZ() + box[2] - REACH_PAD,
                pos.getX() + box[3] + REACH_PAD, pos.getY() + box[4] + REACH_PAD, pos.getZ() + box[5] + REACH_PAD))) {
            seen.culled = 0;
            return true;
        }
        // A culled machine is never drawn, so its box could not grow: draw it now and then to re-measure.
        if (++seen.culled % CULL_PROBE != 0) return false;
        seen.probe = true;
        return true;
    }

    /** Blocks added on every side of the drawn box, for animation between two samples. */
    private static final float REACH_PAD = 3F;
    /** The box is re-measured on one submit in this many; it only ever grows. */
    private static final int REACH_SAMPLE = 8;
    /** A culled machine is drawn (and re-measured) on one check in this many. */
    private static final int CULL_PROBE = 32;

    /** The union of the boxes a block entity's declared parts were drawn in, relative to its block. */
    private static final class Reach {
        final float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE,
                -Float.MAX_VALUE};
        boolean known;
        boolean probe;
        int submits;
        int culled;
        private @Nullable WeakReference<BlockEntity> owner;
        private @Nullable BlockState shape;

        /** Starts again when the block entity or its state is not the one this box was measured for. */
        void attach(BlockEntity blockEntity) {
            if (owner != null && owner.get() == blockEntity && shape == blockEntity.getBlockState()) return;
            owner = new WeakReference<>(blockEntity);
            shape = blockEntity.getBlockState();
            Arrays.fill(box, 0, 3, Float.MAX_VALUE);
            Arrays.fill(box, 3, 6, -Float.MAX_VALUE);
            known = false;
            probe = false;
            submits = 0;
            culled = 0;
        }
    }

    private final Map<Long, Reach> reach = new ConcurrentHashMap<>();
    /** {@code GOLEM_GEM_CULL=off} (or {@code -Dgolem.gem.cull=off}) keeps every machine drawn, to compare frame times. */
    private static final boolean CULLING = !"off".equalsIgnoreCase(
            System.getProperty("golem.gem.cull", System.getenv().getOrDefault("GOLEM_GEM_CULL", "on")));
    private static @Nullable Boolean shaderPack;
    /** Whether {@link #submitUndeclared} is overridden; known after the first submit. */
    private @Nullable Boolean undeclared;
    private boolean undeclaredBase;

    /**
     * Whether the frustum may drop this machine. Not before the first submit has shown that nothing outside
     * the declaration is drawn (its extent is unknown), and not with a shader pack mod installed, whose shadow
     * pass draws what the camera cannot see.
     */
    private boolean cullable() {
        return CULLING && gem().isOffScreen() && Boolean.FALSE.equals(undeclared) && !shaderPackInstalled();
    }

    private static boolean shaderPackInstalled() {
        Boolean known = shaderPack;
        if (known == null) {
            known = Platform.isModLoaded("iris") || Platform.isModLoaded("oculus");
            shaderPack = known;
        }
        return known;
    }

    private void learnReach(S state) {
        BlockPos pos = state.blockPos;
        if (pos == null) return;
        Reach seen = reach.computeIfAbsent(pos.asLong(), key -> new Reach());
        if (seen.known && !seen.probe && seen.submits++ % REACH_SAMPLE != 0) return;
        seen.probe = false;
        float[] box = seen.box;
        Gem.extents(gem(), state, new Matrix4f(), corner -> {
            box[0] = Math.min(box[0], corner.x());
            box[1] = Math.min(box[1], corner.y());
            box[2] = Math.min(box[2], corner.z());
            box[3] = Math.max(box[3], corner.x());
            box[4] = Math.max(box[4], corner.y());
            box[5] = Math.max(box[5], corner.z());
            seen.known = true;
        });
    }
}
