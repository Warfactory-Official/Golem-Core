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
        EMISSIVE_TRANSLUCENT,
        ADDITIVE,
        SOURCE_ALPHA_ADDITIVE
    }

    public enum Align {
        CAMERA,
        Y_LOCKED
    }

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

    @FunctionalInterface
    public interface Rows {
        void write(Frame frame, LayerWriter writer);
    }

    @FunctionalInterface
    public interface Liveness<P> {
        boolean isFinished(P pool);
    }

    /** Shared declarations are common data; only their client consumption crosses the boundary. */
    static record Pool<P>(List<Layer> layers, LayeredSource<P> source, Liveness<P> liveness) {}

    private static final Map<Class<?>, Pool<?>> POOLS = new IdentityHashMap<>();
    private static final Map<Object, Object> ACTIVE = new IdentityHashMap<>();

    private Billboards() {}

    static Pool<?> pool(Class<?> type) {
        return POOLS.get(type);
    }

    static Object active(Object key) {
        return ACTIVE.get(key);
    }

    static void activePut(Object key, Object value) {
        ACTIVE.put(key, value);
    }

    static Object activeRemove(Object key) {
        return ACTIVE.remove(key);
    }

    public static int frameId() {
        //? if >=26.1 {
        if (!dev.golem.core.platform.Platform.isClient()) return -1;
        return Client.frameId();
        //?} else {
        /*return -1;
        *///?}
    }

    public static <T extends Entity> void register(EntityType<T> type, String texture, Source<T> source) {
        register(type, List.of(new Layer(texture, Mode.DEPTH_WRITING_TRANSLUCENT)),
                (entity, frame, layers) -> source.write(entity, frame, layers.layer(0)));
    }

    public static <T extends Entity> void register(EntityType<T> type, List<Layer> layers,
                                                   LayeredSource<T> source) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        List<Layer> declaredLayers = List.copyOf(layers);
        if (declaredLayers.isEmpty()) throw new IllegalArgumentException("Billboard registration has no layers");
        //? if >=26.1 {
        if (dev.golem.core.platform.Platform.isClient()) {
            Client.register(type, declaredLayers, source);
        }
        //?}
    }

    public static <P> void registerPool(Class<P> type, List<Layer> layers, LayeredSource<P> source,
                                        Liveness<P> liveness) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(liveness, "liveness");
        List<Layer> declaredLayers = List.copyOf(layers);
        if (declaredLayers.isEmpty()) throw new IllegalArgumentException("Billboard registration has no layers");
        POOLS.put(type, new Pool<>(declaredLayers, source, liveness));
    }

    public static <P> void add(Level level, P pool) {
        Objects.requireNonNull(pool, "pool");
        //? if >=26.1 {
        if (level.isClientSide() && dev.golem.core.platform.Platform.isClient()) {
            Client.add(level, pool);
        }
        //?}
    }

    public static void remove(Level level, Object pool) {
        //? if >=26.1 {
        if (level.isClientSide() && dev.golem.core.platform.Platform.isClient()) {
            Client.remove(level, pool);
        }
        //?}
    }

    public static boolean owns(Object pool) {
        //? if >=26.1 {
        if (pool instanceof Entity entity && !entity.level().isClientSide()) return false;
        if (!dev.golem.core.platform.Platform.isClient()) return false;
        return Client.owns(pool);
        //?} else {
        /*return false;
        *///?}
    }

    public static boolean owns(Entity entity) {
        //? if >=26.1 {
        if (!entity.level().isClientSide()
                || !dev.golem.core.platform.Platform.isClient()) return false;
        return Client.owns(entity);
        //?} else {
        /*return false;
        *///?}
    }

    //? if >=26.1 {
    private static final class Client {

        private Client() {}

        private static int frameId() {
            return ClientBillboards.frameId();
        }

        private static <T extends Entity> void register(EntityType<T> type, List<Layer> layers,
                                                         LayeredSource<T> source) {
            ClientBillboards.register(type, layers, source);
        }

        private static <P> void add(Level level, P pool) {
            ClientBillboards.add(level, pool);
        }

        private static void remove(Level level, Object pool) {
            ClientBillboards.remove(level, pool);
        }

        private static boolean owns(Object pool) {
            return ClientBillboards.owns(pool);
        }

        private static boolean owns(Entity entity) {
            return ClientBillboards.owns(entity);
        }
    }
    //?}
}
