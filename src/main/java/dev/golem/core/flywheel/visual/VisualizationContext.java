// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

import dev.golem.core.flywheel.DrawManager;
import net.minecraft.world.level.Level;

public interface VisualizationContext {

    Level level();

    VisualEmbedding embedding();

    VisualEmbedding createEmbedding();

    DrawManager drawManager();
}
//?}
