package dev.golem.core.content;

import dev.golem.core.GolemCore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Ordered content phases shared by every Golem mod. A mod declares its phases in {@code init}; any Golem mod subscribes
 * in {@code init} with {@link #on}. Golem Core posts each stage once: {@link Phase.Stage#BEFORE_FLUSH} after every
 * {@code init}, {@link Phase.Stage#AFTER_SETUP} after every {@code setup}. Phases post in declaration order, handlers in
 * subscription order (= Golem mod dependency order), {@link #onLast} handlers after. A handler for an already-posted
 * phase runs at once (late script loads); registers it fills after the flush go through {@code DeferredRegister.late}.
 */
public final class Pipeline {

    private Pipeline() {}

    private static final List<Phase<?>> PHASES = new ArrayList<>();
    private static final Map<Phase<?>, List<Subscription<?>>> HANDLERS = new LinkedHashMap<>();
    private static final Map<Phase<?>, List<Subscription<?>>> LAST = new LinkedHashMap<>();
    private static final Set<Phase<?>> POSTED = new HashSet<>();

    /** A handler's registration; {@link #remove()} before re-subscribing on a script reload. */
    public static final class Subscription<E> {
        private final Phase<E> phase;
        private final Consumer<? super E> handler;

        private Subscription(Phase<E> phase, Consumer<? super E> handler) {
            this.phase = phase;
            this.handler = handler;
        }

        public void remove() {
            synchronized (Pipeline.class) {
                HANDLERS.get(phase).remove(this);
                LAST.get(phase).remove(this);
            }
        }
    }

    @SafeVarargs
    public static synchronized void declare(Phase<?>... phases) {
        for (Phase<?> phase : phases) {
            if (HANDLERS.containsKey(phase)) throw new IllegalStateException("Phase declared twice: " + phase);
            PHASES.add(phase);
            HANDLERS.put(phase, new ArrayList<>());
            LAST.put(phase, new ArrayList<>());
        }
    }

    public static synchronized <E> Subscription<E> on(Phase<E> phase, Consumer<? super E> handler) {
        return subscribe(HANDLERS, phase, handler);
    }

    /** After every {@link #on} handler: modpack scripts, which must see (and may undo) every mod's content. */
    public static synchronized <E> Subscription<E> onLast(Phase<E> phase, Consumer<? super E> handler) {
        return subscribe(LAST, phase, handler);
    }

    private static <E> Subscription<E> subscribe(Map<Phase<?>, List<Subscription<?>>> table, Phase<E> phase,
            Consumer<? super E> handler) {
        List<Subscription<?>> handlers = table.get(phase);
        if (handlers == null) throw new IllegalStateException("Undeclared phase " + phase);
        Subscription<E> subscription = new Subscription<>(phase, handler);
        handlers.add(subscription);
        if (POSTED.contains(phase)) post(phase, List.of(subscription));
        return subscription;
    }

    public static synchronized boolean posted(Phase<?> phase) {
        return POSTED.contains(phase);
    }

    /** Golem Core's bootstrap: every declared phase of {@code stage}, in order. */
    public static synchronized void postStage(Phase.Stage stage) {
        for (Phase<?> phase : new ArrayList<>(PHASES)) {
            if (phase.stage() != stage || !POSTED.add(phase)) continue;
            post(phase, all(phase));
        }
    }

    /** Resets then re-posts replayable phases to every current handler; unposted ones are left to the bootstrap. */
    @SafeVarargs
    public static synchronized void reload(Phase<?>... phases) {
        for (Phase<?> phase : phases) {
            if (!phase.isReplayable()) throw new IllegalArgumentException(phase + " is not replayable");
            if (!POSTED.contains(phase)) continue;
            phase.resets.forEach(Runnable::run);
            post(phase, all(phase));
        }
    }

    private static List<Subscription<?>> all(Phase<?> phase) {
        List<Subscription<?>> all = new ArrayList<>(HANDLERS.get(phase));
        all.addAll(LAST.get(phase));
        return all;
    }

    @SuppressWarnings("unchecked")
    private static <E> void post(Phase<E> phase, List<Subscription<?>> handlers) {
        E event = phase.create();
        for (Subscription<?> subscription : handlers) {
            try {
                ((Consumer<? super E>) subscription.handler).accept(event);
            } catch (RuntimeException e) {
                GolemCore.LOG.error("Content phase {} handler failed", phase, e);
            }
        }
        for (Consumer<? super E> hook : phase.posted) {
            try {
                hook.accept(event);
            } catch (RuntimeException e) {
                GolemCore.LOG.error("Content phase {} hook failed", phase, e);
            }
        }
        dev.golem.core.registry.DeferredRegister.flushLate();
    }
}
