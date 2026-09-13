// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if >=26.1 {

import java.util.function.BooleanSupplier;

public final class ConditionalPlan<C> implements SimplyComposedPlan<C>, DirectPlan<C> {

    private final BooleanSupplier condition;
    private final Plan<C> onTrue;
    private final Plan<C> onFalse;

    public ConditionalPlan(BooleanSupplier condition, Plan<C> onTrue, Plan<C> onFalse) {
        this.condition = condition;
        this.onTrue = onTrue;
        this.onFalse = onFalse;
    }

    public static <C> ConditionalPlan<C> of(BooleanSupplier condition, Plan<C> onTrue, Plan<C> onFalse) {
        return new ConditionalPlan<>(condition, onTrue, onFalse);
    }

    public static <C> ConditionalPlan<C> of(BooleanSupplier condition, Plan<C> onTrue) {
        return new ConditionalPlan<>(condition, onTrue, Plan.unit());
    }

    Plan<C> selectPlan() {
        return condition.getAsBoolean() ? onTrue : onFalse;
    }

    @Override
    public boolean supportsDirectExecution() {
        return DirectPlanSupport.supports(onTrue) && DirectPlanSupport.supports(onFalse);
    }

    @Override
    public void executeDirect(TaskExecutor executor, C context) {
        DirectPlanSupport.execute(selectPlan(), executor, context);
    }

    @Override
    public void execute(TaskExecutor executor, C context, Runnable done) {
        selectPlan().execute(executor, context, done);
    }
}
//?}
