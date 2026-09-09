package dev.golem.core.flywheel.plan;

//? if neoforge && >=26.1 {

import java.util.concurrent.ForkJoinPool;

public enum CommonPoolTaskExecutor implements TaskExecutor {
    INSTANCE;

    @Override
    public int threadCount() {
        return Math.max(1, ForkJoinPool.getCommonPoolParallelism());
    }

    @Override
    public void execute(Runnable command) {
        ForkJoinPool.commonPool().execute(command);
    }

    @Override
    public ForkJoinPool forkJoinPool() {
        return ForkJoinPool.commonPool();
    }
}
//?}
