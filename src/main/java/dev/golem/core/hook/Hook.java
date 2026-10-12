package dev.golem.core.hook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * A point where a mod lets other mods react to, or change, what happens: one listener type {@code T}, any number of
 * listeners, called in a defined order. Every callback Golem Core and the mods built on it offer is a {@code Hook}, so
 * every hook has the same rules when several mods listen.
 *
 * <pre>{@code
 * // the mod that owns the moment declares it once
 * public static final Hook<CanBurn> CAN_BURN = Hook.of(listeners -> (level, pos) -> {
 *     for (CanBurn listener : listeners) if (!listener.canBurn(level, pos)) return false;
 *     return true;
 * });
 * // ... and asks at the moment itself
 * if (!CAN_BURN.invoker().canBurn(level, pos)) return;
 * // another mod listens
 * CAN_BURN.register((level, pos) -> Atmosphere.hasOxygen(level, pos));
 * }</pre>
 *
 * <p><b>Order.</b> Listeners run phase by phase and, within a phase, in registration order. Every hook has the
 * {@link #DEFAULT_PHASE}; a hook may declare more when created, and any mod may add its own phase (it runs after the phases that exist until ordered) and order it
 * against others with {@link #addPhaseOrdering} (for example {@code "ntmspace:atmosphere"} after
 * {@code "hbm:defaults"}). Phases are namespaced strings; an ordering that would form a cycle is rejected.
 *
 * <p><b>Combining.</b> The hook's combiner turns the ordered listeners into one invoker of type {@code T}; it decides
 * what several answers mean (first veto wins, first non-{@link Verdict#PASS} wins, all are called, ...). Owners that
 * only notify can iterate {@link #listeners()} instead.
 *
 * <p>Registration is rare and may happen from any thread (NeoForge and Forge may construct mods in parallel); calls
 * are hot and read one volatile snapshot.
 */
public final class Hook<T> implements Iterable<T> {

    /** The phase a listener registered without one goes to. */
    public static final String DEFAULT_PHASE = "default";

    private final Function<List<T>, T> combiner;
    private final Map<String, List<T>> byPhase = new LinkedHashMap<>(); // guarded by this
    private final Map<String, Set<String>> after = new LinkedHashMap<>(); // phase -> phases that must run first
    private volatile List<T> ordered = List.of();
    private volatile T invoker;

    private Hook(Function<List<T>, T> combiner, String... phases) {
        this.combiner = Objects.requireNonNull(combiner);
        String previous = null;
        for (String phase : phases) {
            byPhase.putIfAbsent(phase, new ArrayList<>());
            if (previous != null) after.computeIfAbsent(phase, p -> new LinkedHashSet<>()).add(previous);
            previous = phase;
        }
        byPhase.putIfAbsent(DEFAULT_PHASE, new ArrayList<>());
        rebuild();
    }

    /** A hook with only the default phase; {@code combiner} turns the ordered listeners into one invoker. */
    public static <T> Hook<T> of(Function<List<T>, T> combiner) {
        return new Hook<>(combiner, DEFAULT_PHASE);
    }

    /**
     * A hook with phases that run in the given order; include {@link #DEFAULT_PHASE} where plain registrations belong
     * (it is appended last if absent).
     */
    public static <T> Hook<T> withPhases(Function<List<T>, T> combiner, String... phases) {
        return new Hook<>(combiner, phases);
    }

    /**
     * A notify-only hook: its invoker is never used, owners iterate {@link #listeners()}. The combiner still must
     * produce a {@code T}; this one fails if asked, so a mistaken {@code invoker()} call is loud.
     */
    public static <T> Hook<T> notifying() {
        return new Hook<>(listeners -> null, DEFAULT_PHASE);
    }

    /** Registers {@code listener} in the default phase. */
    public void register(T listener) {
        register(DEFAULT_PHASE, listener);
    }

    /** Registers {@code listener} in {@code phase}, creating the phase (unordered against others) if new. */
    public synchronized void register(String phase, T listener) {
        byPhase.computeIfAbsent(Objects.requireNonNull(phase), p -> new ArrayList<>()).add(Objects.requireNonNull(listener));
        rebuild();
    }

    /** Makes every listener of {@code first} run before every listener of {@code second}. */
    public synchronized void addPhaseOrdering(String first, String second) {
        if (first.equals(second)) throw new IllegalArgumentException("a phase cannot run before itself: " + first);
        byPhase.computeIfAbsent(first, p -> new ArrayList<>());
        byPhase.computeIfAbsent(second, p -> new ArrayList<>());
        Set<String> prerequisites = after.computeIfAbsent(second, p -> new LinkedHashSet<>());
        if (!prerequisites.add(first)) return;
        try {
            rebuild();
        } catch (IllegalStateException cycle) {
            prerequisites.remove(first);
            rebuild();
            throw new IllegalArgumentException("ordering " + first + " before " + second + " forms a cycle", cycle);
        }
    }

    /** The combined listener: call it at the moment this hook stands for. */
    public T invoker() {
        return invoker;
    }

    /** Every listener in call order (phase order, then registration order); an immutable snapshot. */
    public List<T> listeners() {
        return ordered;
    }

    /** Iterates {@link #listeners()}: owners that only notify loop over the hook. */
    @Override
    public java.util.Iterator<T> iterator() {
        return ordered.iterator();
    }

    /** Whether anything listens; lets an owner skip work no one will see. */
    public boolean isEmpty() {
        return ordered.isEmpty();
    }

    private void rebuild() {
        List<T> list = new ArrayList<>();
        for (String phase : phaseOrder()) list.addAll(byPhase.get(phase));
        ordered = List.copyOf(list);
        invoker = combiner.apply(ordered);
    }

    // Phases in an order honouring every "after" edge; among phases free to run, the one declared or first used
    // earliest, so the order is the same on every run.
    private List<String> phaseOrder() {
        List<String> order = new ArrayList<>();
        Set<String> remaining = new LinkedHashSet<>(byPhase.keySet());
        while (!remaining.isEmpty()) {
            String next = null;
            for (String phase : remaining) {
                if (order.containsAll(after.getOrDefault(phase, Set.of()))) {
                    next = phase;
                    break;
                }
            }
            if (next == null) throw new IllegalStateException("hook phases " + remaining + " depend on each other");
            order.add(next);
            remaining.remove(next);
        }
        return order;
    }
}
