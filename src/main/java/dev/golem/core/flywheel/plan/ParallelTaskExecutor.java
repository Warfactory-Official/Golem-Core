package dev.golem.core.flywheel.plan;

//? if >=26.1 {

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ParallelTaskExecutor implements TaskExecutor, AutoCloseable {

    private final ExecutorService executor;
    private final int threadCount;

    public ParallelTaskExecutor(String name, int threadCount) {
        this.threadCount = Math.max(1, threadCount);
        AtomicInteger index = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, name + "-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newFixedThreadPool(this.threadCount, factory);
    }

    @Override
    public int threadCount() {
        return threadCount;
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
//?}
