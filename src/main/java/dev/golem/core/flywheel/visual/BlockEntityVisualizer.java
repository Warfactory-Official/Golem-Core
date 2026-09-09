// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

import net.minecraft.world.level.block.entity.BlockEntity;

@FunctionalInterface
public interface BlockEntityVisualizer<T extends BlockEntity> {

    BlockEntityVisual<T> createVisual(VisualizationContext context, T blockEntity, float partialTick);
}
//?}
