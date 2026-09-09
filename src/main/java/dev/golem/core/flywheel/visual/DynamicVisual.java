// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

import dev.golem.core.flywheel.plan.Plan;

public interface DynamicVisual extends Visual {

    Plan<Context> planFrame();

    interface Context extends FrameContext {
    }
}
//?}
