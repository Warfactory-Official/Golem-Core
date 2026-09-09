// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if neoforge && >=26.1 {

import java.util.function.BiConsumer;
import java.util.function.Function;

public final class ForEachSlicePlan<T, C> implements SimplyComposedPlan<C>, DirectPlan<C> {

    private final Function<C, T[]> supplier;
    private final BiConsumer<Distribute.Slice<T>, C> action;

    public ForEachSlicePlan(Function<C, T[]> supplier, BiConsumer<Distribute.Slice<T>, C> action) {
        this.supplier = supplier;
        this.action = action;
    }

    public static <T, C> ForEachSlicePlan<T, C> of(Function<C, T[]> supplier, BiConsumer<Distribute.Slice<T>, C> action) {
        return new ForEachSlicePlan<>(supplier, action);
    }

    @Override
    public boolean supportsDirectExecution() {
        return true;
    }

    @Override
    public void executeDirect(TaskExecutor executor, C context) {
        T[] values = supplier.apply(context);
        if (values.length != 0) {
            action.accept(new Distribute.Slice<>(values, 0, values.length), context);
        }
    }

    @Override
    public void execute(TaskExecutor executor, C context, Runnable done) {
        executor.execute(() -> Distribute.slices(executor, context, done, supplier.apply(context), action));
    }
}
//?}
