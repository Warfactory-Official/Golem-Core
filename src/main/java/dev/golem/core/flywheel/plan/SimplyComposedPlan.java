// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if neoforge && >=26.1 {

public interface SimplyComposedPlan<C> extends Plan<C> {

    @Override
    default Plan<C> then(Plan<C> next) {
        return BarrierPlan.of(this, next);
    }

    @Override
    default Plan<C> and(Plan<C> other) {
        return NestedPlan.of(this, other);
    }
}
//?}
