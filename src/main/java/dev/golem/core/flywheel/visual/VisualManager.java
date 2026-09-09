// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

import dev.golem.core.flywheel.plan.PlanMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
public final class VisualManager<K, V extends Visual> {

    private final Object2ObjectOpenHashMap<K, V> byKey = new Object2ObjectOpenHashMap<>();
    private final PlanMap<V, DynamicVisual.Context> framePlans = new PlanMap<>();
    private final PlanMap<V, TickableVisual.Context> tickPlans = new PlanMap<>();
    private final ObjectOpenHashSet<ShaderLightVisual> lightVisuals = new ObjectOpenHashSet<>();
    private final SectionTracker sections;

    public VisualManager(SectionTracker sections) {
        this.sections = sections;
    }

    public synchronized V get(K key) {
        return byKey.get(key);
    }

    public synchronized boolean contains(K key) {
        return byKey.containsKey(key);
    }

    public synchronized void add(K key, V visual) {
        V previous = byKey.put(key, visual);
        if (previous != null && previous != visual) {
            removeVisual(previous);
        }
        if (visual instanceof DynamicVisual dynamic) {
            framePlans.add(visual, dynamic.planFrame());
        }
        if (visual instanceof TickableVisual tickable) {
            tickPlans.add(visual, tickable.planTick());
        }
        if (visual instanceof ShaderLightVisual lit) {
            lightVisuals.add(lit);
        }
        sections.update(visual);
    }

    public synchronized void remove(K key) {
        V visual = byKey.remove(key);
        if (visual == null) return;
        removeVisual(visual);
    }

    public synchronized void refreshSections(K key) {
        V visual = byKey.get(key);
        if (visual != null) {
            sections.update(visual);
        }
    }

    public synchronized void snapshot(List<V> into) {
        into.addAll(byKey.values());
    }

    public synchronized void clear() {
        List<V> snapshot = new ArrayList<>(byKey.values());
        byKey.clear();
        framePlans.clear();
        tickPlans.clear();
        lightVisuals.clear();
        for (V visual : snapshot) {
            sections.remove(visual);
            visual.delete();
        }
    }

    public synchronized void collectLights(ShaderLightVisual.LightCollector collector) {
        for (ShaderLightVisual lit : lightVisuals) {
            lit.collectLights(collector);
        }
    }

    PlanMap<V, DynamicVisual.Context> framePlans() {
        return framePlans;
    }

    PlanMap<V, TickableVisual.Context> tickPlans() {
        return tickPlans;
    }

    private void removeVisual(V visual) {
        if (visual instanceof DynamicVisual) framePlans.remove(visual);
        if (visual instanceof TickableVisual) tickPlans.remove(visual);
        if (visual instanceof ShaderLightVisual lit) lightVisuals.remove(lit);
        sections.remove(visual);
        visual.delete();
    }
}
//?}
