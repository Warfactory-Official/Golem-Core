package dev.golem.core.flywheel.plan;

//? if >=26.1 {

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;

public final class PlanRunner {

    private PlanRunner() {
    }

    public static <C> void runBlocking(Plan<C> plan, TaskExecutor executor, C context) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        TaskExecutor failFastExecutor = failFast(executor, failure);
        try {
            plan.execute(failFastExecutor, context, latch::countDown);
        } catch (Throwable throwable) {
            failure.compareAndSet(null, throwable);
            latch.countDown();
        }
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        Throwable throwable = failure.get();
        if (throwable != null) {
            throw unchecked(throwable);
        }
    }

    public static <C> CompletableFuture<Void> runAsync(Plan<C> plan, TaskExecutor executor, C context) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        TaskExecutor failFastExecutor = failFast(executor, failure);
        try {
            plan.execute(failFastExecutor, context, () -> {
                Throwable t = failure.get();
                if (t != null) {
                    future.completeExceptionally(t);
                } else {
                    future.complete(null);
                }
            });
        } catch (Throwable throwable) {
            failure.compareAndSet(null, throwable);
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private static TaskExecutor failFast(TaskExecutor executor, AtomicReference<Throwable> failure) {
        return new TaskExecutor() {
            @Override
            public int threadCount() {
                return executor.threadCount();
            }

            @Override
            public ForkJoinPool forkJoinPool() {
                return executor.forkJoinPool();
            }

            @Override
            public void recordFailure(Throwable throwable) {
                failure.compareAndSet(null, throwable);
                executor.recordFailure(throwable);
            }

            @Override
            public void execute(Runnable command) {
                executor.execute(() -> {
                    try {
                        command.run();
                    } catch (Throwable throwable) {
                        failure.compareAndSet(null, throwable);
                        throw unchecked(throwable);
                    }
                });
            }
        };
    }

    private static RuntimeException unchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        return new RuntimeException(throwable);
    }
}
//?}
