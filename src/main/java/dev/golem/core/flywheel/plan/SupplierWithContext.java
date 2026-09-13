// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if >=26.1 {

import java.util.function.Function;
import java.util.function.Supplier;

@FunctionalInterface
public interface SupplierWithContext<C, R> extends Function<C, R> {

    R get(C context);

    @Override
    default R apply(C c) {
        return get(c);
    }

    @FunctionalInterface
    interface Ignored<C, R> extends SupplierWithContext<C, R>, Supplier<R> {

        @Override
        R get();

        @Override
        default R get(C ignored) {
            return get();
        }
    }
}
//?}
