package dev.golem.core.render.gem;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.golem.core.render.CameraInfo;
import dev.golem.core.render.CompatBER;
import dev.golem.core.render.CompatRenderState;
import dev.golem.core.render.SubmitSink;
import dev.golem.core.render.gem.Gem.Model;
import dev.golem.core.render.Visuals;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
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
        }
        if (tracing) trace(state.blockPos, owned);
        submitUndeclared(state, poseStack, sink, camera);
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
    protected void submitUndeclared(S state, PoseStack poseStack, SubmitSink sink, CameraInfo camera) {}

    @Override
    public int viewDistance() {
        return gem().viewDistance();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return gem().isOffScreen();
    }

    /**
     * A model that draws outside its own block is never distance-culled here; declaring
     * {@link Gem.Model#offScreen} already put it in the level's globally-rendered set, and culling it
     * again by its one owning cell is what made multiblock machines vanish when that cell scrolled off.
     */
    @Override
    public boolean shouldRender(T blockEntity, Vec3 cameraPosition) {
        return gem().isOffScreen()
                || Vec3.atCenterOf(blockEntity.getBlockPos()).closerThan(cameraPosition, viewDistance());
    }
}
