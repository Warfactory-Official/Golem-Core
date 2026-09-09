// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if neoforge && >=26.1 {

public final class RunnablePlan<C> implements SimplyComposedPlan<C>, DirectPlan<C> {

    private final RunnableWithContext<C> task;

    public RunnablePlan(RunnableWithContext<C> task) {
        this.task = task;
    }

    public static <C> RunnablePlan<C> of(RunnableWithContext<C> task) {
        return new RunnablePlan<>(task);
    }

    @Override
    public boolean supportsDirectExecution() {
        return true;
    }

    @Override
    public void executeDirect(TaskExecutor executor, C context) {
        task.run(context);
    }

    @Override
    public void execute(TaskExecutor executor, C context, Runnable done) {
        executor.execute(() -> {
            task.run(context);
            done.run();
        });
    }
}
//?}
