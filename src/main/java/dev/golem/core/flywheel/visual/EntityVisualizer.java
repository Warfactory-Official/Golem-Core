// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import net.minecraft.world.entity.Entity;

@FunctionalInterface
public interface EntityVisualizer<T extends Entity> {

    EntityVisual<T> createVisual(VisualizationContext context, T entity, float partialTick);
}
//?}
