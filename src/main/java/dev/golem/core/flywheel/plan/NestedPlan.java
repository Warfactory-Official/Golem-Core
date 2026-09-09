// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if neoforge && >=26.1 {

public final class NestedPlan<C> implements SimplyComposedPlan<C>, DirectPlan<C>, ExpandablePlan<C> {

    private final Plan<C>[] plans;

    @SafeVarargs
    public static <C> NestedPlan<C> of(Plan<C>... plans) {
        return new NestedPlan<>(plans);
    }

    private NestedPlan(Plan<C>[] plans) {
        this.plans = plans;
    }

    @Override
    public boolean supportsDirectExecution() {
        return DirectPlanSupport.all(plans);
    }

    @Override
    public void executeDirect(TaskExecutor executor, C context) {
        for (Plan<C> plan : plans) {
            DirectPlanSupport.execute(plan, executor, context);
        }
    }

    @Override
    public Plan<C>[] expand(TaskExecutor executor, C context) {
        return plans;
    }

    @Override
    public void execute(TaskExecutor executor, C context, Runnable done) {
        int count = plans.length;
        if (count == 0) {
            done.run();
            return;
        }
        if (count == 1) {
            plans[0].execute(executor, context, done);
            return;
        }
        TaskSynchronizer sync = new TaskSynchronizer(count, done);
        for (Plan<C> plan : plans) {
            plan.execute(executor, context, sync);
        }
    }

    @Override
    public Plan<C> and(Plan<C> other) {
        @SuppressWarnings("unchecked")
        Plan<C>[] merged = new Plan[plans.length + 1];
        System.arraycopy(plans, 0, merged, 0, plans.length);
        merged[plans.length] = other;
        return new NestedPlan<>(merged);
    }
}
//?}
