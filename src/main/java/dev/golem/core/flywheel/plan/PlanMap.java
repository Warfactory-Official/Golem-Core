// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.plan;

//? if >=26.1 {

import java.util.Arrays;

public final class PlanMap<K, C> implements SimplyComposedPlan<C>, ExpandablePlan<C> {

    private Object[] keys = new Object[16];
    private Plan<C>[] plans = newPlans(16);
    private int size;

    public synchronized void add(K key, Plan<C> plan) {
        int index = indexOf(key);
        if (index >= 0) {
            plans[index] = plan;
            return;
        }
        ensureCapacity(size + 1);
        keys[size] = key;
        plans[size] = plan;
        size++;
    }

    public synchronized void remove(K key) {
        int index = indexOf(key);
        if (index < 0) return;
        int last = size - 1;
        keys[index] = keys[last];
        plans[index] = plans[last];
        keys[last] = null;
        plans[last] = null;
        size = last;
    }

    public synchronized void clear() {
        Arrays.fill(keys, 0, size, null);
        Arrays.fill(plans, 0, size, null);
        size = 0;
    }

    @Override
    public void execute(TaskExecutor executor, C context, Runnable done) {
        Distribute.plans(executor, context, done, expand(executor, context));
    }

    @Override
    public synchronized Plan<C>[] expand(TaskExecutor executor, C context) {
        return Arrays.copyOf(plans, size);
    }

    private int indexOf(K key) {
        for (int i = 0; i < size; i++) {
            if (keys[i] == key) return i;
        }
        return -1;
    }

    private void ensureCapacity(int required) {
        if (required <= keys.length) return;
        int newCapacity = keys.length << 1;
        while (newCapacity < required) {
            newCapacity <<= 1;
        }
        keys = Arrays.copyOf(keys, newCapacity);
        plans = Arrays.copyOf(plans, newCapacity);
    }

    @SuppressWarnings("unchecked")
    private static <C> Plan<C>[] newPlans(int size) {
        return new Plan[size];
    }
}
//?}
