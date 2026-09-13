// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import net.minecraft.world.entity.Entity;

public abstract class EntityVisual<T extends Entity> implements Visual {

    protected final T entity;

    protected EntityVisual(T entity) {
        this.entity = entity;
    }

    public final T entity() {
        return entity;
    }
}
//?}
