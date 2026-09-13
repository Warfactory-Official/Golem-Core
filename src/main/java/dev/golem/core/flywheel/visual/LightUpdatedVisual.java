// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if >=26.1 {

// Implemented by visuals that cache lighting from block/sky sources and need to be poked when a
// tracked section's lighting changes. The listener is scheduled via the normal tick plan pipeline.
public interface LightUpdatedVisual {

    void updateLight(long sectionKey);
}
//?}
