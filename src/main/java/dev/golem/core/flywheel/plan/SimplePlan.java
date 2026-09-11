// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if neoforge && >=26.1 {

public final class SimplePlan<C> implements SimplyComposedPlan<C>, DirectPlan<C> {

    private final RunnableWithContext<C>[] tasks;

    @SafeVarargs
    public static <C> SimplePlan<C> of(RunnableWithContext<C>... tasks) {
        return new SimplePlan<>(tasks);
    }

    private SimplePlan(RunnableWithContext<C>[] tasks) {
        this.tasks = tasks;
    }

    @Override
    public boolean supportsDirectExecution() {
        return true;
    }

    @Override
    public void executeDirect(TaskExecutor executor, C context) {
        for (RunnableWithContext<C> task : tasks) {
            task.run(context);
        }
    }

    @Override
    public void execute(TaskExecutor executor, C context, Runnable done) {
        int count = tasks.length;
        if (count == 0) {
            done.run();
            return;
        }
        if (count == 1) {
            RunnableWithContext<C> task = tasks[0];
            executor.execute(() -> {
                try {
                    task.run(context);
                } finally {
                    done.run();
                }
            });
            return;
        }
        TaskSynchronizer sync = new TaskSynchronizer(count, done);
        for (RunnableWithContext<C> task : tasks) {
            executor.execute(() -> {
                try {
                    task.run(context);
                } finally {
                    sync.completeOne();
                }
            });
        }
    }
}
//?}
