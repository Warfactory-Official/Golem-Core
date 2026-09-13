// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if >=26.1 {

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public interface TaskExecutor extends Executor {

    int threadCount();

    default ForkJoinPool forkJoinPool() {
        return null;
    }

    default void recordFailure(Throwable throwable) {
    }
}
//?}
