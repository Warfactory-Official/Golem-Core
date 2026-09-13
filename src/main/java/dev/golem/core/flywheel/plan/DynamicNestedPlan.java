// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if >=26.1 {

import java.util.Collection;

public record DynamicNestedPlan<C>(
        SupplierWithContext<C, Collection<? extends Plan<C>>> plans) implements SimplyComposedPlan<C>, ExpandablePlan<C> {

    public static <C> DynamicNestedPlan<C> of(SupplierWithContext.Ignored<C, Collection<? extends Plan<C>>> supplier) {
        return new DynamicNestedPlan<>(supplier);
    }

    public static <C> DynamicNestedPlan<C> of(SupplierWithContext<C, Collection<? extends Plan<C>>> supplier) {
        return new DynamicNestedPlan<>(supplier);
    }

    @Override
    public Plan<C>[] expand(TaskExecutor executor, C context) {
        Collection<? extends Plan<C>> snapshot = plans.get(context);
        int size = snapshot.size();
        @SuppressWarnings("unchecked")
        Plan<C>[] expanded = new Plan[size];
        int i = 0;
        for (Plan<C> plan : snapshot) {
            expanded[i++] = plan;
        }
        return expanded;
    }

    @Override
    public void execute(TaskExecutor executor, C context, Runnable done) {
        Collection<? extends Plan<C>> snapshot = plans.get(context);
        int count = snapshot.size();
        if (count == 0) {
            done.run();
            return;
        }
        if (count == 1) {
            snapshot.iterator().next().execute(executor, context, done);
            return;
        }

        TaskSynchronizer sync = new TaskSynchronizer(count, done);
        for (Plan<C> plan : snapshot) {
            plan.execute(executor, context, sync);
        }
    }
}
//?}
