// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if >=26.1 {

public interface Plan<C> {

    void execute(TaskExecutor executor, C context, Runnable done);

    Plan<C> then(Plan<C> next);

    Plan<C> and(Plan<C> other);

    default void execute(TaskExecutor executor, C context) {
        execute(executor, context, () -> {});
    }

    static <C> Plan<C> unit() {
        return UnitPlan.instance();
    }
}
//?}
