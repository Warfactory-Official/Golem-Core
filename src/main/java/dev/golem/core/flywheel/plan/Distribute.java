// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if >=26.1 {

import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public final class Distribute {

    private Distribute() {
    }

    public static <C, T> void tasks(TaskExecutor executor, C context, Runnable onCompletion, T[] values, BiConsumer<T, C> action) {
        int size = values.length;
        if (size == 0) {
            onCompletion.run();
            return;
        }

        int sliceSize = sliceSize(executor, size);

        if (size <= sliceSize) {
            for (T value : values) {
                action.accept(value, context);
            }
            onCompletion.run();
        } else if (sliceSize == 1) {
            TaskSynchronizer sync = new TaskSynchronizer(size, onCompletion);
            for (T value : values) {
                executor.execute(() -> {
                    try {
                        action.accept(value, context);
                    } finally {
                        sync.completeOne();
                    }
                });
            }
        } else {
            int sliceCount = ceilingDiv(size, sliceSize);
            TaskSynchronizer sync = new TaskSynchronizer(sliceCount, onCompletion);
            int remaining = size;
            while (remaining > 0) {
                int end = remaining;
                remaining -= sliceSize;
                int start = Math.max(remaining, 0);
                int sliceStart = start;
                int sliceEnd = end;
                executor.execute(() -> {
                    try {
                        for (int i = sliceStart; i < sliceEnd; i++) {
                            action.accept(values[i], context);
                        }
                    } finally {
                        sync.completeOne();
                    }
                });
            }
        }
    }

    public static <C, T> void slices(TaskExecutor executor, C context, Runnable onCompletion, T[] values, BiConsumer<Slice<T>, C> action) {
        int size = values.length;
        if (size == 0) {
            onCompletion.run();
            return;
        }

        int sliceSize = sliceSize(executor, size);

        if (size <= sliceSize) {
            action.accept(new Slice<>(values, 0, size), context);
            onCompletion.run();
        } else if (sliceSize == 1) {
            TaskSynchronizer sync = new TaskSynchronizer(size, onCompletion);
            for (int i = 0; i < size; i++) {
                int idx = i;
                executor.execute(() -> {
                    try {
                        action.accept(new Slice<>(values, idx, idx + 1), context);
                    } finally {
                        sync.completeOne();
                    }
                });
            }
        } else {
            int sliceCount = ceilingDiv(size, sliceSize);
            TaskSynchronizer sync = new TaskSynchronizer(sliceCount, onCompletion);
            int remaining = size;
            while (remaining > 0) {
                int end = remaining;
                remaining -= sliceSize;
                int start = Math.max(remaining, 0);
                int sliceStart = start;
                int sliceEnd = end;
                executor.execute(() -> {
                    try {
                        action.accept(new Slice<>(values, sliceStart, sliceEnd), context);
                    } finally {
                        sync.completeOne();
                    }
                });
            }
        }
    }

    public static <C> void plans(TaskExecutor executor, C context, Runnable onCompletion, Plan<C>[] plans) {
        int size = plans.length;
        if (size == 0) {
            onCompletion.run();
            return;
        }
        ForkJoinPool forkJoinPool = executor.forkJoinPool();
        if (forkJoinPool != null) {
            int threshold = Math.max(1, sliceSize(executor, size, 4));
            PlanRangeTask<C> task = new PlanRangeTask<>(null, plans, 0, size, threshold, context, onCompletion, executor, new AtomicReference<>());
            if (ForkJoinTask.getPool() == forkJoinPool) {
                task.compute();
            } else {
                forkJoinPool.execute(task);
            }
            return;
        }
        if (size == 1) {
            plans[0].execute(executor, context, onCompletion);
            return;
        }

        int sliceSize = sliceSize(executor, size, 4);

        if (size <= sliceSize) {
            TaskSynchronizer sync = new TaskSynchronizer(size, onCompletion);
            for (Plan<C> plan : plans) {
                plan.execute(executor, context, sync);
            }
        } else if (sliceSize == 1) {
            TaskSynchronizer sync = new TaskSynchronizer(size, onCompletion);
            for (Plan<C> plan : plans) {
                executor.execute(() -> executePlan(plan, executor, context, sync));
            }
        } else {
            int sliceCount = ceilingDiv(size, sliceSize);
            TaskSynchronizer sync = new TaskSynchronizer(sliceCount, onCompletion);
            int remaining = size;
            while (remaining > 0) {
                int end = remaining;
                remaining -= sliceSize;
                int start = Math.max(remaining, 0);
                int sliceStart = start;
                int sliceEnd = end;
                executor.execute(() -> executePlanSlice(plans, sliceStart, sliceEnd, executor, context, sync));
            }
        }
    }

    public static int sliceSize(TaskExecutor executor, int totalSize) {
        return sliceSize(executor, totalSize, 32);
    }

    public static int sliceSize(TaskExecutor executor, int totalSize, int denominator) {
        return ceilingDiv(totalSize, executor.threadCount() * denominator);
    }

    private static int ceilingDiv(int num, int denom) {
        return (num + denom - 1) / denom;
    }

    private static <C> void executePlanSlice(Plan<C>[] plans, int start, int end, TaskExecutor executor, C context, Runnable done) {
        SmallTaskSynchronizer sync = new SmallTaskSynchronizer(end - start, done);
        for (int i = start; i < end; i++) {
            java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean();
            Runnable complete = () -> {
                if (completed.compareAndSet(false, true)) {
                    sync.completeOne();
                }
            };
            try {
                executePlan(plans[i], executor, context, complete);
            } catch (Throwable throwable) {
                complete.run();
                throw throwable;
            }
        }
    }

    private static <C> void executePlan(Plan<C> plan, TaskExecutor executor, C context, Runnable done) {
        if (DirectPlanSupport.supports(plan)) {
            try {
                DirectPlanSupport.execute(plan, executor, context);
            } finally {
                done.run();
            }
            return;
        }
        plan.execute(executor, context, done);
    }

    private static final class PlanRangeTask<C> extends CountedCompleter<Void> {

        private final Plan<C>[] plans;
        private final int startInclusive;
        private final int endExclusive;
        private final int threshold;
        private final C context;
        private final Runnable onCompletion;
        private final TaskExecutor executor;
        private final AtomicReference<Throwable> failure;

        private PlanRangeTask(CountedCompleter<?> parent, Plan<C>[] plans, int startInclusive, int endExclusive, int threshold, C context, Runnable onCompletion, TaskExecutor executor, AtomicReference<Throwable> failure) {
            super(parent);
            this.plans = plans;
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
            this.threshold = threshold;
            this.context = context;
            this.onCompletion = onCompletion;
            this.executor = executor;
            this.failure = failure;
        }

        @Override
        public void compute() {
            int start = startInclusive;
            int end = endExclusive;
            try {
                while (failure.get() == null) {
                    int size = end - start;
                    if (size <= threshold) {
                        runPlans(start, end);
                        tryComplete();
                        return;
                    }
                    int mid = (start + end) >>> 1;
                    addToPendingCount(1);
                    new PlanRangeTask<>(this, plans, mid, end, threshold, context, onCompletion, executor, failure).fork();
                    end = mid;
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
            tryComplete();
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            if (getCompleter() != null) {
                return;
            }
            Throwable throwable = failure.get();
            if (throwable != null) {
                executor.recordFailure(throwable);
            }
            onCompletion.run();
        }

        private void runPlans(int start, int end) {
            for (int i = start; i < end; i++) {
                if (failure.get() != null) {
                    return;
                }
                runPlanTyped(plans[i], context);
            }
        }

        private <D> void runPlanTyped(Plan<D> plan, D planContext) {
            switch (plan) {
                case ExpandablePlan<D> expandable -> {
                    Plan<D>[] expanded = expandable.expand(executor, planContext);
                    int size = expanded.length;
                    if (size == 0) {
                        return;
                    }
                    if (size == 1) {
                        runPlanTyped(expanded[0], planContext);
                        return;
                    }
                    addToPendingCount(1);
                    new PlanRangeTask<>(this, expanded, 0, size, threshold, planContext, onCompletion, executor, failure).fork();
                }
                case ConditionalPlan<D> conditional -> runPlanTyped(conditional.selectPlan(), planContext);
                case IfElsePlan<D>(var condition, var onTrue, var onFalse) -> runPlanTyped(condition.getAsBoolean(planContext) ? onTrue : onFalse, planContext);
                case MapContextPlan<D, ?> mapped -> runMappedPlan(mapped, planContext);
                case DirectPlan<D> direct when direct.supportsDirectExecution() -> direct.executeDirect(executor, planContext);
                default -> {
                    addToPendingCount(1);
                    new OpaquePlanTask<>(this, plan, executor, planContext, failure).start();
                }
            }
        }

        private <D, E> void runMappedPlan(MapContextPlan<D, E> mapped, D planContext) {
            runPlanTyped(mapped.plan(), mapped.map().get(planContext));
        }
    }

    private static final class OpaquePlanTask<C> extends CountedCompleter<Void> implements Runnable {

        private final Plan<C> plan;
        private final TaskExecutor executor;
        private final C context;
        private final AtomicReference<Throwable> failure;

        private OpaquePlanTask(CountedCompleter<?> parent, Plan<C> plan, TaskExecutor executor, C context, AtomicReference<Throwable> failure) {
            super(parent);
            this.plan = plan;
            this.executor = executor;
            this.context = context;
            this.failure = failure;
        }

        private void start() {
            try {
                plan.execute(executor, context, this);
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
                tryComplete();
            }
        }

        @Override
        public void compute() {
        }

        @Override
        public void run() {
            tryComplete();
        }
    }

    public record Slice<T>(T[] values, int startInclusive, int endExclusive) {
    }
}
//?}
