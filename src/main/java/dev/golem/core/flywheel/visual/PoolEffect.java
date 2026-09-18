package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import dev.golem.core.render.Billboards;

/** The engine-side identity of one {@link Billboards.Pool}, so the pool itself names no engine type. */
public final class PoolEffect<P> implements Effect {

    private final P pool;
    private final Billboards.Liveness liveness;

    public PoolEffect(P pool, Billboards.Liveness liveness) {
        this.pool = pool;
        this.liveness = liveness;
    }

    public P pool() {
        return pool;
    }

    @Override
    public boolean isDead() {
        return liveness.isFinished(pool);
    }
}
//?}
