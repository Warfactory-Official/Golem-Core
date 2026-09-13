package dev.golem.core.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Which of an owner's ids are currently up, so a caller can tell a rising edge from a repeat.
 *
 * <p>Separate from its one caller because that caller cannot be reached without a running server,
 * and this is the half whose states are worth stating. Owners are compared by identity: the one
 * real owner is a level, and two levels are never equal without being the same object.
 */
final class RisingEdges<O> {

    private final Map<O, LongOpenHashSet> up = new IdentityHashMap<>();

    /** Marks the id up, and answers whether that was a change. */
    boolean rise(O owner, long id) {
        return up.computeIfAbsent(owner, o -> new LongOpenHashSet()).add(id);
    }

    /** Marks the id down. Unknown ids are already down. */
    void fall(O owner, long id) {
        LongOpenHashSet ids = up.get(owner);
        if (ids == null) return;
        ids.remove(id);
        if (ids.isEmpty()) up.remove(owner);
    }

    /** Drops everything recorded for one owner. */
    void forget(O owner) {
        up.remove(owner);
    }

    /** Whether this id is currently up. */
    boolean isUp(O owner, long id) {
        LongOpenHashSet ids = up.get(owner);
        return ids != null && ids.contains(id);
    }

    /** Ids currently up, across every owner. Exposed so a test can assert the table is bounded. */
    int size() {
        int total = 0;
        for (LongOpenHashSet ids : up.values()) total += ids.size();
        return total;
    }
}
