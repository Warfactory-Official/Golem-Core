package dev.golem.core.content;

import dev.golem.core.GolemCore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Edits to a table that rebuilds itself (a recipe table after {@code reset()}): recorded per table key (its instance or
 * class), replayed on every build by {@link #apply}. {@link #content} ops persist; {@link #edit} ops are dropped by
 * {@link #clearEdits} (reload). An op for a table already built also runs at once. Ops run outside the lock.
 */
public final class Replay {

    private Replay() {}

    private record Op(String description, Runnable action) {}

    private static final Map<Object, List<Op>> CONTENT = new HashMap<>();
    private static final Map<Object, List<Op>> EDITS = new HashMap<>();
    private static final Set<Object> BUILT = new HashSet<>();

    public static void content(Object table, String description, Runnable action) {
        record(CONTENT, table, new Op(description, action));
    }

    public static void edit(Object table, String description, Runnable action) {
        record(EDITS, table, new Op(description, action));
    }

    /** End of a table's build: its content ops, then its edits. */
    public static void apply(Object table) {
        List<Op> ops;
        synchronized (Replay.class) {
            BUILT.add(table);
            ops = new ArrayList<>(CONTENT.getOrDefault(table, List.of()));
            ops.addAll(EDITS.getOrDefault(table, List.of()));
        }
        ops.forEach(Replay::run);
    }

    /** Drops every edit and forgets which tables built; they rebuild on next use. */
    public static synchronized void clearEdits() {
        EDITS.clear();
        BUILT.clear();
    }

    private static void record(Map<Object, List<Op>> ops, Object table, Op op) {
        synchronized (Replay.class) {
            ops.computeIfAbsent(table, k -> new ArrayList<>()).add(op);
            if (!BUILT.contains(table)) return;
        }
        run(op);
    }

    private static void run(Op op) {
        try {
            op.action().run();
        } catch (RuntimeException e) {
            GolemCore.LOG.error("Replayed edit '{}' failed", op.description(), e);
        }
    }
}
