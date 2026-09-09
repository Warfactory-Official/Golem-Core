// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if neoforge && >=26.1 {

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

// Sharded completion counter via LongAdder to defuse contention on the completion path. The
// original AtomicInteger.decrementAndGet bounced a single cache line K-ways per completion —
// each x86 lock xadd invalidates the line in every other core's L1, so under K worker threads
// finishing tasks simultaneously the per-call cost grows roughly linearly with K. LongAdder
// hashes each thread to its own internal Cell (each Cell is @Contended-padded to its own
// cache line in HotSpot), so concurrent completions touch disjoint lines and the per-call
// cost stays ~constant regardless of K.
//
// Direction inverted: we count UP and compare against target instead of counting DOWN to
// zero, because LongAdder is designed for increments. sum() walks all cells (~1-2 ns per
// cell, ~32 ns total at peak fan-out) so we short-circuit with the fired flag once done has
// already run. The CAS on `fired` guarantees done fires exactly once even if multiple threads
// race past the threshold simultaneously.
// Implements Runnable so callers can pass the synchronizer itself as a completion callback
// instead of `this::completeOne`. The latter is a capturing method reference that allocates a
// fresh wrapper at every call site invocation; passing the synchronizer directly eliminates
// that per-task alloc — a major hit in Distribute.plans where the inner-loop method ref was
// allocated per plan iteration.
//
// Fire-exactly-once proof for `completed.increment(); if (sum() >= target) CAS-fire`:
//
//   "At most once": AtomicBoolean.compareAndSet(false, true) succeeds for at most one caller.
//
//   "At least once" (by contradiction): suppose every caller's sum() under-reads. Take any
//   two threads A, B with target=2. Let inc_A retire at t1, sum_A start at t3 > t1 (program
//   order); inc_B retire at t2, sum_B start at t4 > t2. For sum_A to miss B requires t3 < t2;
//   for sum_B to miss A requires t4 < t1. Combined: t1 < t3 < t2 AND t2 < t4 < t1, so
//   t1 < t2 AND t2 < t1 — contradiction. Generalises to N: the sum() with the latest
//   real-time start runs after all preceding increments retired; release-acquire on
//   Cell.value (volatile via VarHandle inside LongAdder) makes those writes visible to it.
//
//   Cell-array growth doesn't break the proof. Striped64.longAccumulate writes the new
//   Cell's value before publishing the new cells reference, and growth copies REFERENCES
//   to existing cells into the new array — so a sum() holding an older cells reference
//   still walks every Cell that existed at capture time. Only brand-new cells from a
//   still-in-progress growth could be missed, and only by sums that started before that
//   growth completed; the growing thread's own subsequent sum() runs after growth
//   publishes and sees its own contribution.
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
