// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class VisualizerRegistry {

    private static final Map<BlockEntityType<?>, BlockEntityVisualizer<?>> BLOCK_ENTITY = new HashMap<>();
    private static final Map<EntityType<?>, EntityVisualizer<?>> ENTITY = new HashMap<>();
    private static final Map<Class<? extends Effect>, EffectVisualizer<?>> EFFECT = new HashMap<>();

    private VisualizerRegistry() {
    }

    public static <T extends BlockEntity> void register(BlockEntityType<T> type, BlockEntityVisualizer<T> visualizer) {
        BLOCK_ENTITY.put(type, visualizer);
    }

    public static <T extends Entity> void register(EntityType<T> type, EntityVisualizer<T> visualizer) {
        ENTITY.put(type, visualizer);
    }

    public static <E extends Effect> void register(Class<E> type, EffectVisualizer<E> visualizer) {
        EFFECT.put(type, visualizer);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> BlockEntityVisualizer<T> get(BlockEntityType<T> type) {
        return (BlockEntityVisualizer<T>) BLOCK_ENTITY.get(type);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> EntityVisualizer<T> get(EntityType<T> type) {
        return (EntityVisualizer<T>) ENTITY.get(type);
    }

    @SuppressWarnings("unchecked")
    public static <E extends Effect> EffectVisualizer<E> get(Class<E> type) {
        return (EffectVisualizer<E>) EFFECT.get(type);
    }
}
//?}
