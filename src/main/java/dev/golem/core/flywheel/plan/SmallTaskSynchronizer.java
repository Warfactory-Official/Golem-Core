package dev.golem.core.flywheel.plan;

//? if neoforge && >=26.1 {

import java.util.concurrent.atomic.AtomicInteger;

final class SmallTaskSynchronizer implements Runnable {

    private final AtomicInteger remaining;
    private final Runnable done;

    SmallTaskSynchronizer(int count, Runnable done) {
        remaining = new AtomicInteger(count);
        this.done = done;
    }

    @Override
    public void run() {
        completeOne();
    }

    void completeOne() {
        if (remaining.decrementAndGet() == 0) {
            done.run();
        }
    }
}
//?}
