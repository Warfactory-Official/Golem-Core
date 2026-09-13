package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import net.minecraft.util.RandomSource;

public final class TickContext implements TickableVisual.Context {

    public static final TickContext INSTANCE = new TickContext();

    // One RandomSource per pool worker thread, lazily initialized on first access. Common-pool
    // workers are stable for the JVM's lifetime so the ThreadLocal entries don't accumulate.
    private static final ThreadLocal<RandomSource> WORKER_RANDOM = ThreadLocal.withInitial(RandomSource::create);

    private TickContext() {
    }

    @Override
    public RandomSource random() {
        return WORKER_RANDOM.get();
    }
}
//?}
