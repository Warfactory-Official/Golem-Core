package dev.golem.core.render;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;

/** Optional camera-facing billboard instances without exposing an engine implementation. */
public final class Billboards {

    @FunctionalInterface
    public interface RowSource {
        void write(RowWriter writer);
    }

    @FunctionalInterface
    public interface RowWriter {
        void row(double x, double y, double z, float halfSize,
                 float red, float green, float blue, float alpha, int packedLight);

        /**
         * A row showing one cell of a {@code columns x rows} sprite sheet, stitched column-major.
         *
         * <p>Defaulted so a writer with nothing to animate stays a lambda; the instanced backend
         * overrides it onto the per-instance UV transform, which is what makes an animated puff of
         * smoke one bucket rather than one per frame of its strip.
         */
        default void sprite(double x, double y, double z, float halfSize,
                            float red, float green, float blue, float alpha, int packedLight,
                            int column, int row, int columns, int rows) {
            row(x, y, z, halfSize, red, green, blue, alpha, packedLight);
        }
    }

    public record Frame(float partialTick, Vec3 cameraPosition, Quaternionfc cameraOrientation) {}

    @FunctionalInterface
    public interface Source<T> {
        void write(T subject, Frame frame, RowWriter writer);
    }

    public enum Mode {
        DEPTH_WRITING_TRANSLUCENT,
        /** Blended, unlit and not depth-writing — what {@code entityTranslucentEmissive} draws. */
        EMISSIVE_TRANSLUCENT,
        ADDITIVE,
        SOURCE_ALPHA_ADDITIVE
    }

    /** How a layer's quad is oriented. */
    public enum Align {
        /** Square to the camera. */
        CAMERA,
        /**
         * 1.7's vertical lock: the camera's horizontal right, but an up vector whose Y is forced to
         * one, so a column of smoke leans rather than lying down when the player looks up at it.
         */
        Y_LOCKED
    }

    /**
     * Which corner of the quad the texture's origin lands on.
     *
     * <p>1.7 wrote both conventions and neither is more correct: the Torex cloudlets and the muke
     * start at the bottom-left-is-(1,1) corner, {@code ParticleGiblet} at the opposite one. A layer
     * says which so the two backends put the texture the same way up.
     */
    public enum Uv {
        STANDARD,
        ROTATED_180
    }

    public record Layer(String texture, Mode mode, Align align, Uv uv) {
        public Layer {
            if (texture == null || texture.isBlank()) throw new IllegalArgumentException("Blank billboard texture");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(align, "align");
            Objects.requireNonNull(uv, "uv");
        }

        public Layer(String texture, Mode mode) {
            this(texture, mode, Align.CAMERA, Uv.STANDARD);
        }

        public Layer(String texture, Mode mode, Align align) {
            this(texture, mode, align, Uv.STANDARD);
        }

        public Layer(String texture, Mode mode, Uv uv) {
            this(texture, mode, Align.CAMERA, uv);
        }
    }

    @FunctionalInterface
    public interface LayerWriter {
        RowWriter layer(int index);
    }

    @FunctionalInterface
    public interface LayeredSource<T> {
        void write(T subject, Frame frame, LayerWriter writer);
    }

    /** One frame's rows, already bound to whatever supplies them. */
    @FunctionalInterface
    public interface Rows {
        void write(Frame frame, LayerWriter writer);
    }

    /** Whether a pool has finished, so the engine can release its instances. */
    @FunctionalInterface
    public interface Liveness<P> {
        boolean isFinished(P pool);
    }

    private record Pool<P>(List<Layer> layers, LayeredSource<P> source, Liveness<P> liveness) {}

    private static final Map<Class<?>, Pool<?>> POOLS = new IdentityHashMap<>();
    private static final Map<Object, Object> ACTIVE = new IdentityHashMap<>();

    private Billboards() {}

    /** Monotonic client render-frame id where the vendored provider exists, otherwise -1. */
    public static int frameId() {
        //? if >=26.1 {
        return dev.golem.core.flywheel.visual.GolemRenderSystem.currentFrameId();
        //?} else {
        /*return -1;
        *///?}
    }

    /** Registers an entity type whose billboard rows may be supplied during vanilla submission. */
    public static <T extends Entity> void register(EntityType<T> type, String texture, Source<T> source) {
        register(type, List.of(new Layer(texture, Mode.DEPTH_WRITING_TRANSLUCENT)),
                (entity, frame, layers) -> source.write(entity, frame, layers.layer(0)));
    }

    /** Registers atomically owned billboard layers, drawn in their material order. */
    public static <T extends Entity> void register(EntityType<T> type, List<Layer> layers,
                                                   LayeredSource<T> source) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        List<Layer> declaredLayers = List.copyOf(layers);
        if (declaredLayers.isEmpty()) throw new IllegalArgumentException("Billboard registration has no layers");
        //? if >=26.1 {
        dev.golem.core.flywheel.visual.BillboardEntityVisual.register(
                type, declaredLayers, source);
        //?}
    }

    /**
     * Declares a client-owned billboard pool — a particle system, a detonation, a spread of casings.
     *
     * <p>The entity form above keys its visual by the entity; a pool has nothing to be keyed by, so it
     * is keyed by identity and swept when {@code liveness} says it is finished. Registration is by
     * class so one declaration serves every pool of that kind, exactly as an entity type does.
     */
    public static <P> void registerPool(Class<P> type, List<Layer> layers, LayeredSource<P> source,
                                        Liveness<P> liveness) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(liveness, "liveness");
        List<Layer> declaredLayers = List.copyOf(layers);
        if (declaredLayers.isEmpty()) throw new IllegalArgumentException("Billboard registration has no layers");
        POOLS.put(type, new Pool<>(declaredLayers, source, liveness));
    }

    /** Hands a pool to the instanced backend. A no-op wherever Core ships no engine. */
    public static <P> void add(Level level, P pool) {
        Objects.requireNonNull(pool, "pool");
        //? if >=26.1 {
        @SuppressWarnings("unchecked")
        Pool<P> declaration = (Pool<P>) POOLS.get(pool.getClass());
        if (declaration == null) return;
        var manager = dev.golem.core.flywheel.visual.VisualizationLifecycle.get(level);
        if (manager == null) return;
        var held = (dev.golem.core.flywheel.visual.PoolEffect<?>) ACTIVE.get(pool);
        if (held != null && manager.effectVisual(held) != null) return;
        var effect = new dev.golem.core.flywheel.visual.PoolEffect<>(pool, declaration.liveness());
        manager.addEffect(effect, (context, added, partialTick) ->
                        new dev.golem.core.flywheel.visual.BillboardEffectVisual<>(
                                context, added, declaration.layers(), declaration.source()),
                dev.golem.core.client.Frames.partialTick());
        ACTIVE.put(pool, effect);
        //?}
    }

    /** Releases a pool's instances ahead of its liveness test, for a level change or a reload. */
    public static void remove(Level level, Object pool) {
        //? if >=26.1 {
        var effect = (dev.golem.core.flywheel.visual.PoolEffect<?>) ACTIVE.remove(pool);
        if (effect == null) return;
        var manager = dev.golem.core.flywheel.visual.VisualizationLifecycle.get(level);
        if (manager != null) manager.removeEffect(effect);
        //?}
    }

    /**
     * Whether the instanced backend owns this pool's rows for the current frame. False whenever no
     * healthy visual holds it, so the vanilla path draws instead.
     */
    public static boolean owns(Object pool) {
        //? if >=26.1 {
        var effect = (dev.golem.core.flywheel.visual.PoolEffect<?>) ACTIVE.get(pool);
        if (effect == null) return false;
        var manager = dev.golem.core.flywheel.visual.VisualizationLifecycle.current();
        if (manager == null) return false;
        var visual = manager.effectVisual(effect);
        return visual instanceof dev.golem.core.flywheel.visual.BillboardEffectVisual<?> billboards
                && billboards.ownsCurrentFrame();
        //?} else {
        /*return false;
        *///?}
    }

    /**
     * Updates this entity's GPU rows and claims its billboard layer for the current frame.
     * Returns false whenever no healthy visual owns the entity, so callers can render normally.
     */
    public static boolean owns(Entity entity) {
        //? if >=26.1 {
        var manager = dev.golem.core.flywheel.visual.VisualizationLifecycle.get(entity.level());
        if (manager == null) return false;
        var visual = manager.entityVisual(entity.getUUID());
        if (visual instanceof dev.golem.core.flywheel.visual.BillboardEntityVisual<?> billboards) {
            return billboards.ownsCurrentFrame();
        }
        return false;
        //?} else {
        /*return false;
        *///?}
    }
}
