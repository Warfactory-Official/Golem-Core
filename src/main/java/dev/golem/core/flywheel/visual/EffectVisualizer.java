// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

@FunctionalInterface
public interface EffectVisualizer<E extends Effect> {

    EffectVisual<E> createVisual(VisualizationContext context, E effect, float partialTick);
}
//?}
