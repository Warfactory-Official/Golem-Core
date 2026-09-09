// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if neoforge && >=26.1 {

public record MapContextPlan<C, D>(SupplierWithContext<C, D> map,
                                   Plan<D> plan) implements SimplyComposedPlan<C>, DirectPlan<C> {

    public static <C, D> Builder<C, D> map(SupplierWithContext<C, D> map) {
        return new Builder<>(map);
    }

    public static <C, D> Builder<C, D> get(SupplierWithContext.Ignored<C, D> map) {
        return new Builder<>(map);
    }

    @Override
    public boolean supportsDirectExecution() {
        return DirectPlanSupport.supports(plan);
    }

    @Override
    public void executeDirect(TaskExecutor executor, C context) {
        DirectPlanSupport.execute(plan, executor, map.get(context));
    }

    @Override
    public void execute(TaskExecutor executor, C context, Runnable done) {
        plan.execute(executor, map.get(context), done);
    }

    public static final class Builder<C, D> {

        private final SupplierWithContext<C, D> map;

        public Builder(SupplierWithContext<C, D> map) {
            this.map = map;
        }

        public MapContextPlan<C, D> to(Plan<D> plan) {
            return new MapContextPlan<>(map, plan);
        }

        public MapContextPlan<C, D> plan() {
            return new MapContextPlan<>(map, UnitPlan.instance());
        }
    }
}
//?}
