// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if >=26.1 {

public record IfElsePlan<C>(BooleanSupplierWithContext<C> condition, Plan<C> onTrue,
                            Plan<C> onFalse) implements SimplyComposedPlan<C>, DirectPlan<C> {

    public static <C> Builder<C> on(BooleanSupplierWithContext<C> condition) {
        return new Builder<>(condition);
    }

    public static <C> Builder<C> on(BooleanSupplierWithContext.Ignored<C> condition) {
        return new Builder<>(condition);
    }

    @Override
    public boolean supportsDirectExecution() {
        return DirectPlanSupport.supports(onTrue) && DirectPlanSupport.supports(onFalse);
    }

    @Override
    public void executeDirect(TaskExecutor executor, C context) {
        DirectPlanSupport.execute(condition.getAsBoolean(context) ? onTrue : onFalse, executor, context);
    }

    @Override
    public void execute(TaskExecutor executor, C context, Runnable done) {
        if (condition.getAsBoolean(context)) {
            onTrue.execute(executor, context, done);
        } else {
            onFalse.execute(executor, context, done);
        }
    }

    public static final class Builder<C> {

        private final BooleanSupplierWithContext<C> condition;
        private Plan<C> onTrue = UnitPlan.instance();
        private Plan<C> onFalse = UnitPlan.instance();

        public Builder(BooleanSupplierWithContext<C> condition) {
            this.condition = condition;
        }

        public Builder<C> ifTrue(Plan<C> onTrue) {
            this.onTrue = onTrue;
            return this;
        }

        public Builder<C> ifFalse(Plan<C> onFalse) {
            this.onFalse = onFalse;
            return this;
        }

        public IfElsePlan<C> plan() {
            return new IfElsePlan<>(condition, onTrue, onFalse);
        }
    }
}
//?}
