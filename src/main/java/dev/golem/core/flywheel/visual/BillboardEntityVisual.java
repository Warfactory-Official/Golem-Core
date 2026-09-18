package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import dev.golem.core.render.Billboards;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Engine adapter for billboard rows synchronously supplied by an entity renderer. */
public final class BillboardEntityVisual<T extends Entity> extends EntityVisual<T>
        implements SynchronousDynamicVisual {

    private final Billboards.LayeredSource<T> source;
    private final BillboardLayers layers;

    private BillboardEntityVisual(VisualizationContext context, T entity, List<Billboards.Layer> definitions,
                                   Billboards.LayeredSource<T> source) {
        super(entity);
        this.source = source;
        this.layers = new BillboardLayers(context, definitions);
    }

    public static <T extends Entity> void register(EntityType<T> type, List<Billboards.Layer> layers,
                                                    Billboards.LayeredSource<T> source) {
        VisualizerRegistry.register(type, (context, entity, partialTick) ->
                new BillboardEntityVisual<>(context, entity, layers, source));
    }

    @Override
    public void beginFrame(DynamicVisual.Context frame) {
        layers.beginFrame(frame, (billboardFrame, writer) -> source.write(entity, billboardFrame, writer),
                entity::getType);
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
