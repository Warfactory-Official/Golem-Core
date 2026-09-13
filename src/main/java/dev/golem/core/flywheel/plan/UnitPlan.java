// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if >=26.1 {

public final class UnitPlan<C> implements Plan<C>, DirectPlan<C> {

    private static final UnitPlan<?> INSTANCE = new UnitPlan<>();

    private UnitPlan() {
    }

    @SuppressWarnings("unchecked")
    public static <C> UnitPlan<C> instance() {
        return (UnitPlan<C>) INSTANCE;
    }

    @Override
    public void execute(TaskExecutor executor, C context, Runnable done) {
        done.run();
    }

    @Override
    public boolean supportsDirectExecution() {
        return true;
    }

    @Override
    public void executeDirect(TaskExecutor executor, C context) {
    }

    @Override
    public Plan<C> then(Plan<C> next) {
        return next;
    }

    @Override
    public Plan<C> and(Plan<C> other) {
        return other;
    }
}
//?}
