// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

public abstract class EffectVisual<E extends Effect> implements Visual {

    protected final E effect;

    protected EffectVisual(E effect) {
        this.effect = effect;
    }

    public final E effect() {
        return effect;
    }
}
//?}
