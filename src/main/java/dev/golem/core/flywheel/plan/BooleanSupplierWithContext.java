// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if >=26.1 {

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

@FunctionalInterface
public interface BooleanSupplierWithContext<C> extends Predicate<C> {

    boolean getAsBoolean(C context);

    @Override
    default boolean test(C c) {
        return getAsBoolean(c);
    }

    @FunctionalInterface
    interface Ignored<C> extends BooleanSupplierWithContext<C>, BooleanSupplier {

        @Override
        boolean getAsBoolean();

        @Override
        default boolean getAsBoolean(C ignored) {
            return getAsBoolean();
        }
    }
}
//?}
