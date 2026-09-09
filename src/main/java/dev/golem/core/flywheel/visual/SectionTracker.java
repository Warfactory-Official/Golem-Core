package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
public final class SectionTracker {

    private final Long2ObjectOpenHashMap<ObjectOpenHashSet<Visual>> bySection = new Long2ObjectOpenHashMap<>();
    private final IdentityHashMap<Visual, long[]> membership = new IdentityHashMap<>();

    public synchronized void update(Visual visual) {
        if (!(visual instanceof SectionTrackedVisual tracked)) return;
        long[] previous = membership.get(visual);
        LongOpenHashSet fresh = new LongOpenHashSet();
        tracked.collectCrumblingSections(fresh::add);
        long[] next = fresh.toLongArray();
        if (previous != null) {
            for (long key : previous) {
                if (fresh.contains(key)) continue;
                removeFromSection(key, visual);
            }
            for (long key : next) {
                boolean alreadyPresent = false;
                for (long p : previous) {
                    if (p == key) {
                        alreadyPresent = true;
                        break;
                    }
                }
                if (!alreadyPresent) {
                    addToSection(key, visual);
                }
            }
        } else {
            for (long key : next) {
                addToSection(key, visual);
            }
        }
        membership.put(visual, next);
    }

    public synchronized void remove(Visual visual) {
        long[] previous = membership.remove(visual);
        if (previous == null) return;
        for (long key : previous) {
            removeFromSection(key, visual);
        }
    }

    public synchronized Collection<Visual> visualsInSection(long sectionKey) {
        ObjectOpenHashSet<Visual> set = bySection.get(sectionKey);
        return set == null ? Collections.emptyList() : new ObjectOpenHashSet<>(set);
    }

    public synchronized long[] membership(Visual visual) {
        long[] arr = membership.get(visual);
        return arr == null ? new long[0] : arr;
    }

    public synchronized void collectAllSections(LongArrayList into) {
        into.addAll(bySection.keySet());
    }

    private void addToSection(long key, Visual visual) {
        bySection.computeIfAbsent(key, k -> new ObjectOpenHashSet<>()).add(visual);
    }

    private void removeFromSection(long key, Visual visual) {
        ObjectOpenHashSet<Visual> set = bySection.get(key);
        if (set == null) return;
        set.remove(visual);
        if (set.isEmpty()) {
            bySection.remove(key);
        }
    }
}
//?}
