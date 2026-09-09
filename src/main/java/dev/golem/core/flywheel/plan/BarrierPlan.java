// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if neoforge && >=26.1 {

public record BarrierPlan<C>(Plan<C> first, Plan<C> second) implements SimplyComposedPlan<C>, DirectPlan<C> {

    public static <C> BarrierPlan<C> of(Plan<C> first, Plan<C> second) {
        return new BarrierPlan<>(first, second);
    }

    @Override
    public boolean supportsDirectExecution() {
        return DirectPlanSupport.supports(first) && DirectPlanSupport.supports(second);
    }

    @Override
    public void executeDirect(TaskExecutor executor, C context) {
        DirectPlanSupport.execute(first, executor, context);
        DirectPlanSupport.execute(second, executor, context);
    }

    @Override
    public void execute(TaskExecutor executor, C context, Runnable done) {
        first.execute(executor, context, () -> second.execute(executor, context, done));
    }
}
//?}
