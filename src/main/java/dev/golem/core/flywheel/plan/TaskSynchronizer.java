// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if >=26.1 {

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

// Sharded completion counter via LongAdder, to avoid contention on the completion path: one
// AtomicInteger.decrementAndGet per completion bounces a single cache line between all K worker
// threads, while LongAdder gives each thread its own padded Cell.
//
// It counts UP and compares against target, because LongAdder is built for increments. sum() walks
// every cell, so the `fired` flag short-circuits it once done has run. The CAS on `fired` makes done
// fire exactly once when several threads pass the threshold together.
//
// Implements Runnable so callers pass the synchronizer itself as the completion callback; a
// `this::completeOne` method reference captures and allocates per call site, which hurt in
// Distribute.plans.
//
// Fire-exactly-once, for `completed.increment(); if (sum() >= target) CAS-fire`:
//   At most once: AtomicBoolean.compareAndSet(false, true) succeeds for one caller.
//   At least once: the sum() with the latest real-time start runs after all earlier increments
//   retired, and release-acquire on Cell.value (volatile inside LongAdder) makes those writes
//   visible to it. (Two threads both under-reading would need t1 < t2 and t2 < t1.)
//   Cell-array growth keeps this: Striped64 writes the new Cell's value before publishing the new
//   array, and growth copies references to existing cells, so a sum() on an older array still walks
//   every cell that existed when it started. The growing thread's own sum() runs after publication.
final class TaskSynchronizer implements Runnable {

    private final int target;
    private final LongAdder completed = new LongAdder();
    private final AtomicBoolean fired = new AtomicBoolean();
    private final Runnable done;

    TaskSynchronizer(int count, Runnable done) {
        this.target = count;
        this.done = done;
    }

    @Override
    public void run() {
        completeOne();
    }

    void completeOne() {
        if (target == 1) {
            if (fired.compareAndSet(false, true)) {
                done.run();
            }
            return;
        }
        if (fired.get()) {
            return;
        }
        completed.increment();
        if (fired.get()) {
            return;
        }
        if (completed.sum() >= target && fired.compareAndSet(false, true)) {
            done.run();
        }
    }
}
//?}
