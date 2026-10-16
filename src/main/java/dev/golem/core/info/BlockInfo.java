package dev.golem.core.info;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * What a mod shows in a block-info overlay (Jade, where installed: {@code dev.golem.core.jade}). Declared once from
 * common init and read on demand: gauges and tanks on the server against the block entity, redirects on the client
 * against the looked-at position. Nothing here names an overlay mod, so a mod declares the same way whether one is
 * present or not.
 */
public final class BlockInfo {

    private record Source<T, V>(Class<T> type, BiConsumer<? super T, Consumer<V>> reader) {
        void read(Object target, Consumer<V> out) {
            if (type.isInstance(target)) reader.accept(type.cast(target), out);
        }
    }

    private static final List<Source<?, Gauge>> GAUGES = new CopyOnWriteArrayList<>();
    private static final List<Source<?, Tank>> TANKS = new CopyOnWriteArrayList<>();
    private static final List<BiFunction<Level, BlockPos, @Nullable BlockPos>> REDIRECTS = new CopyOnWriteArrayList<>();

    private BlockInfo() {}

    /** Gauges for every block entity of {@code type}; several declarations may match one block entity. */
    public static <T> void gauges(Class<T> type, BiConsumer<? super T, Consumer<Gauge>> reader) {
        GAUGES.add(new Source<>(type, reader));
    }

    /** Fluid tanks for every block entity of {@code type}, shown as the overlay's own fluid view. */
    public static <T> void tanks(Class<T> type, BiConsumer<? super T, Consumer<Tank>> reader) {
        TANKS.add(new Source<>(type, reader));
    }

    /** Looks at another position instead, e.g. a multiblock's core for one of its cells; null keeps the position. */
    public static void redirect(BiFunction<Level, BlockPos, @Nullable BlockPos> resolver) {
        REDIRECTS.add(resolver);
    }

    public static boolean declaresGauges(@Nullable Object target) {
        return matches(GAUGES, target);
    }

    public static boolean declaresTanks(@Nullable Object target) {
        return matches(TANKS, target);
    }

    public static List<Gauge> gaugesOf(@Nullable Object target) {
        return read(GAUGES, target);
    }

    public static List<Tank> tanksOf(@Nullable Object target) {
        return read(TANKS, target);
    }

    private static <V> boolean matches(List<Source<?, V>> sources, @Nullable Object target) {
        if (target == null) return false;
        for (Source<?, V> source : sources) if (source.type.isInstance(target)) return true;
        return false;
    }

    private static <V> List<V> read(List<Source<?, V>> sources, @Nullable Object target) {
        List<V> values = new ArrayList<>();
        if (target != null) for (Source<?, V> source : sources) source.read(target, values::add);
        return values;
    }

    public static @Nullable BlockPos redirectOf(Level level, BlockPos pos) {
        for (BiFunction<Level, BlockPos, @Nullable BlockPos> resolver : REDIRECTS) {
            BlockPos target = resolver.apply(level, pos);
            if (target != null && !target.equals(pos)) return target;
        }
        return null;
    }
}
