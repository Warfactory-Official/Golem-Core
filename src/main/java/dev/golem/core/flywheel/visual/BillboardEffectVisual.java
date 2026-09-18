package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import dev.golem.core.render.Billboards;
import java.util.List;

/**
 * Engine adapter for billboard rows supplied by a client-owned pool rather than by an entity.
 *
 * <p>A particle system, a detonation, a spread of spent casings: things the client invents and ticks
 * itself, with no entity and no block entity to key a visual by. The engine's effect manager is
 * exactly that key, and {@link PoolEffect} is what keeps its {@code Effect} type from reaching a
 * consumer that has to compile on five nodes without an engine at all.
 */
public final class BillboardEffectVisual<P> extends EffectVisual<PoolEffect<P>>
        implements SynchronousDynamicVisual {

    private final Billboards.LayeredSource<P> source;
    private final BillboardLayers layers;

    public BillboardEffectVisual(VisualizationContext context, PoolEffect<P> effect,
                                 List<Billboards.Layer> definitions, Billboards.LayeredSource<P> source) {
        super(effect);
        this.source = source;
        this.layers = new BillboardLayers(context, definitions);
    }

    @Override
    public void beginFrame(DynamicVisual.Context frame) {
        layers.beginFrame(frame, (billboardFrame, writer) -> source.write(effect.pool(), billboardFrame, writer),
                () -> effect.pool().getClass().getSimpleName());
    }

    public boolean ownsCurrentFrame() {
        return layers.ownsCurrentFrame();
    }

    @Override
    public void update(float partialTick) {
    }

    @Override
    public void delete() {
        layers.delete();
    }
}
//?}
