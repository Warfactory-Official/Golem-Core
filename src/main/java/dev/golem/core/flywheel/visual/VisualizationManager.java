// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import dev.golem.core.flywheel.DrawManager;
import dev.golem.core.flywheel.backend.BackendManager;
import dev.golem.core.flywheel.plan.NestedPlan;
import dev.golem.core.flywheel.plan.Plan;
import dev.golem.core.flywheel.plan.PlanRunner;
import dev.golem.core.flywheel.plan.TaskExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
public final class VisualizationManager {

    private final Level level;
    private final SectionTracker sections = new SectionTracker();
    private final VisualManager<BlockPos, BlockEntityVisual<?>> blockEntities = new VisualManager<>(sections);
    private final VisualManager<UUID, EntityVisual<?>> entities = new VisualManager<>(sections);
    private final VisualManager<Effect, EffectVisual<?>> effects = new VisualManager<>(sections);
    private final VisualEmbedding rootEmbedding = new VisualEmbedding();
    private final DrawManager drawManager;
    private final ShaderLightBuffer lightBuffer = new ShaderLightBuffer();
    private final Context context;

    public VisualizationManager(Level level) {
        this.level = level;
        var backend = BackendManager.current();
        // Callers ask BackendManager first and create no manager when it answers null; this is the
        // backstop, and it throws the engine-wide kind so the lifecycle's guard switches the engine off.
        if (backend == null) throw new dev.golem.core.flywheel.EngineState.Unsupported("no supported backend");
        this.drawManager = backend.createDrawManager();
        this.context = new Context();
    }

    public Level level() {
        return level;
    }

    public DrawManager drawManager() {
        return drawManager;
    }

    public VisualEmbedding rootEmbedding() {
        return rootEmbedding;
    }

    public SectionTracker sections() {
        return sections;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T extends BlockEntity> void addBlockEntity(T blockEntity, float partialTick) {
        BlockEntityVisualizer<T> visualizer = (BlockEntityVisualizer) VisualizerRegistry.get(blockEntity.getType());
        if (visualizer == null) return;
        BlockEntityVisual<T> visual = visualizer.createVisual(context, blockEntity, partialTick);
        blockEntities.add(blockEntity.getBlockPos(), visual);
    }

    public void removeBlockEntity(BlockPos pos) {
        blockEntities.remove(pos);
    }

    public void refreshBlockEntity(BlockPos pos) {
        blockEntities.refreshSections(pos);
    }

    public @Nullable BlockEntityVisual<?> blockEntityVisual(BlockPos pos) {
        return blockEntities.get(pos);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T extends Entity> void addEntity(T entity, float partialTick) {
        EntityVisualizer<T> visualizer = (EntityVisualizer) VisualizerRegistry.get(entity.getType());
        if (visualizer == null) return;
        EntityVisual<T> visual = visualizer.createVisual(context, entity, partialTick);
        entities.add(entity.getUUID(), visual);
    }

    public void removeEntity(UUID id) {
        entities.remove(id);
    }

    public @Nullable EntityVisual<?> entityVisual(UUID id) {
        return entities.get(id);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <E extends Effect> void addEffect(E effect, float partialTick) {
        EffectVisualizer<E> visualizer = (EffectVisualizer) VisualizerRegistry.get(effect.getClass());
        if (visualizer == null) return;
        EffectVisual<E> visual = visualizer.createVisual(context, effect, partialTick);
        effects.add(effect, visual);
    }

    /**
     * Adds an effect whose visual the caller already built. {@link VisualizerRegistry} keys on the
     * effect's own class, which cannot separate two pools that share one adapter type, so a caller
     * holding its own registration hands the visual over directly.
     */
    public <E extends Effect> void addEffect(E effect, EffectVisualizer<E> visualizer, float partialTick) {
        effects.add(effect, visualizer.createVisual(context, effect, partialTick));
    }

    public @Nullable EffectVisual<?> effectVisual(Effect effect) {
        return effects.get(effect);
    }

    public void removeEffect(Effect effect) {
        effects.remove(effect);
    }

    public void sweepEffects() {
        List<EffectVisual<?>> snapshot = new ArrayList<>();
        effects.snapshot(snapshot);
        for (EffectVisual<?> visual : snapshot) {
            Effect e = visual.effect();
            if (e.isDead()) {
                effects.remove(e);
            }
        }
    }

    public void runTick(TaskExecutor executor) {
        sweepEffects();
        @SuppressWarnings("unchecked")
        Plan<TickableVisual.Context>[] plans = new Plan[] {
                blockEntities.tickPlans(),
                entities.tickPlans(),
                effects.tickPlans()
        };
        PlanRunner.runBlocking(NestedPlan.of(plans), executor, TickContext.INSTANCE);
    }

    public CompletableFuture<Void> runFrameAsync(TaskExecutor executor, DynamicVisual.Context context) {
        @SuppressWarnings("unchecked")
        Plan<DynamicVisual.Context>[] plans = new Plan[] {
                blockEntities.framePlans(),
                entities.framePlans(),
                effects.framePlans()
        };
        return PlanRunner.runAsync(NestedPlan.of(plans).then(drawManager.frameMaintenancePlan()), executor, context);
    }

    /**
     * Runs every visual that has to publish before the asynchronous backend work starts.
     *
     * <p>All three managers, not just entities: a {@link SynchronousDynamicVisual} is not
     * a {@link DynamicVisual}, so it has no frame plan and this walk is its <em>only</em> route to
     * {@code beginFrame}. Walking entities alone would leave every synchronous block entity (which is
     * what a declared model is) holding instances it never wrote, drawing through its vanilla renderer
     * with no error.
     */
    public void runFrameSynchronous(DynamicVisual.Context context) {
        beginSynchronous(blockEntities, context);
        beginSynchronous(entities, context);
        beginSynchronous(effects, context);
    }

    private static <K, V extends Visual> void beginSynchronous(VisualManager<K, V> manager,
                                                               DynamicVisual.Context context) {
        List<V> snapshot = new ArrayList<>();
        manager.snapshot(snapshot);
        for (V visual : snapshot) {
            if (visual instanceof SynchronousDynamicVisual synchronous) synchronous.beginFrame(context);
        }
    }

    public void dispatchLightUpdate(long sectionKey) {
        for (Visual visual : sections.visualsInSection(sectionKey)) {
            if (visual instanceof LightUpdatedVisual lit) {
                lit.updateLight(sectionKey);
            }
        }
    }

    public void collectLights(ShaderLightVisual.LightCollector collector) {
        blockEntities.collectLights(collector);
        entities.collectLights(collector);
        effects.collectLights(collector);
    }

    public ShaderLightBuffer lightBuffer() {
        return lightBuffer;
    }

    public void shutdown() {
        blockEntities.clear();
        entities.clear();
        effects.clear();
        lightBuffer.close();
        drawManager.close();
    }

    public static void collectAabbSections(AABB box, SectionTrackedVisual.SectionCollector collector) {
        int minX = SectionPos.blockToSectionCoord((int) Math.floor(box.minX));
        int minY = SectionPos.blockToSectionCoord((int) Math.floor(box.minY));
        int minZ = SectionPos.blockToSectionCoord((int) Math.floor(box.minZ));
        int maxX = SectionPos.blockToSectionCoord((int) Math.floor(box.maxX));
        int maxY = SectionPos.blockToSectionCoord((int) Math.floor(box.maxY));
        int maxZ = SectionPos.blockToSectionCoord((int) Math.floor(box.maxZ));
        for (int sx = minX; sx <= maxX; sx++) {
            for (int sy = minY; sy <= maxY; sy++) {
                for (int sz = minZ; sz <= maxZ; sz++) {
                    collector.accept(SectionPos.asLong(sx, sy, sz));
                }
            }
        }
    }

    private final class Context implements VisualizationContext {
        @Override
        public Level level() {
            return level;
        }

        @Override
        public VisualEmbedding embedding() {
            return rootEmbedding;
        }

        @Override
        public VisualEmbedding createEmbedding() {
            return new VisualEmbedding();
        }

        @Override
        public DrawManager drawManager() {
            return drawManager;
        }
    }
}
//?}
