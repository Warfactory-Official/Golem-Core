// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if >=26.1 {

import java.util.function.BiConsumer;
import java.util.function.Function;

public final class ForEachPlan<T, C> implements SimplyComposedPlan<C>, DirectPlan<C> {

    private final Function<C, T[]> supplier;
    private final BiConsumer<T, C> action;

    public ForEachPlan(Function<C, T[]> supplier, BiConsumer<T, C> action) {
        this.supplier = supplier;
        this.action = action;
    }

    public static <T, C> ForEachPlan<T, C> of(Function<C, T[]> supplier, BiConsumer<T, C> action) {
        return new ForEachPlan<>(supplier, action);
    }

    @Override
    public boolean supportsDirectExecution() {
        return true;
    }

    @Override
    public void executeDirect(TaskExecutor executor, C context) {
        T[] values = supplier.apply(context);
        for (T value : values) {
            action.accept(value, context);
        }
    }

    @Override
    public void execute(TaskExecutor executor, C context, Runnable done) {
        executor.execute(() -> Distribute.tasks(executor, context, done, supplier.apply(context), action));
    }
}
//?}
