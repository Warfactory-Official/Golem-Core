package dev.golem.core.render;

//? if >=26.1 {

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Client-side implementation kept behind the common {@link Billboards} facade. */
public final class ClientBillboards {

    private ClientBillboards() {}

    public static int frameId() {
        return dev.golem.core.flywheel.visual.GolemRenderSystem.currentFrameId();
    }

    public static <T extends Entity> void register(EntityType<T> type, List<Billboards.Layer> layers,
                                                   Billboards.LayeredSource<T> source) {
        dev.golem.core.flywheel.visual.BillboardEntityVisual.register(type, layers, source);
    }

    public static <P> void add(Level level, P pool) {
        @SuppressWarnings("unchecked")
        Billboards.Pool<P> declaration = (Billboards.Pool<P>) Billboards.pool(pool.getClass());
        if (declaration == null) return;
        var manager = dev.golem.core.flywheel.visual.VisualizationLifecycle.get(level);
        if (manager == null) return;
        var held = (dev.golem.core.flywheel.visual.PoolEffect<?>) Billboards.active(pool);
        if (held != null && manager.effectVisual(held) != null) return;
        var effect = new dev.golem.core.flywheel.visual.PoolEffect<>(pool, declaration.liveness());
        manager.addEffect(effect, (context, added, partialTick) ->
                        new dev.golem.core.flywheel.visual.BillboardEffectVisual<>(
                                context, added, declaration.layers(), declaration.source()),
                dev.golem.core.client.Frames.partialTick());
        Billboards.activePut(pool, effect);
    }

    public static void remove(Level level, Object pool) {
        var effect = (dev.golem.core.flywheel.visual.PoolEffect<?>) Billboards.activeRemove(pool);
        if (effect == null) return;
        var manager = dev.golem.core.flywheel.visual.VisualizationLifecycle.get(level);
        if (manager != null) manager.removeEffect(effect);
    }

    public static boolean owns(Object pool) {
        var effect = (dev.golem.core.flywheel.visual.PoolEffect<?>) Billboards.active(pool);
        if (effect == null) return false;
        var manager = dev.golem.core.flywheel.visual.VisualizationLifecycle.current();
        if (manager == null) return false;
        var visual = manager.effectVisual(effect);
        return visual instanceof dev.golem.core.flywheel.visual.BillboardEffectVisual<?> billboards
                && billboards.ownsCurrentFrame();
    }

    public static boolean owns(Entity entity) {
        var manager = dev.golem.core.flywheel.visual.VisualizationLifecycle.get(entity.level());
        if (manager == null) return false;
        var visual = manager.entityVisual(entity.getUUID());
        if (visual instanceof dev.golem.core.flywheel.visual.BillboardEntityVisual<?> billboards) {
            return billboards.ownsCurrentFrame();
        }
        return false;
    }
}
//?}
