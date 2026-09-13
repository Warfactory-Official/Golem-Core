// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import dev.golem.core.flywheel.plan.Plan;
import net.minecraft.util.RandomSource;

public interface TickableVisual extends Visual {

    Plan<Context> planTick();

    interface Context {

        // Per-worker RandomSource. Tick plans run on the flywheel common-pool executor, so the
        // shared Level random can't be touched (LegacyRandomSource's CAS aborts on contention
        // with the level tick / worldgen / lighting on the main thread). The implementation
        // hands back a ThreadLocal-backed RandomSource so each pool worker has its own
        // contention-free instance reused across ticks.
        RandomSource random();
    }
}
//?}
