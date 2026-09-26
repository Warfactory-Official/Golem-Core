package dev.golem.core.content;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * One step of a Golem mod's content pipeline; see {@link Pipeline}. The event is created fresh per post. A
 * {@linkplain #replayable() replayable} phase is re-posted by {@link Pipeline#reload} after its reset hooks run.
 */
public final class Phase<E> {

    public enum Stage {
        /** After every Golem mod's {@code init}, before any registry flush: may register content. */
        BEFORE_FLUSH,
        /** After every Golem mod's {@code setup}: registries are live. */
        AFTER_SETUP
    }

    private final String id;
    private final Stage stage;
    private final Supplier<? extends E> event;
    private final boolean replayable;
    final List<Runnable> resets = new ArrayList<>();
    final List<java.util.function.Consumer<? super E>> posted = new ArrayList<>();

    private Phase(String id, Stage stage, Supplier<? extends E> event, boolean replayable) {
        this.id = id;
        this.stage = stage;
        this.event = event;
        this.replayable = replayable;
    }

    public static <E> Phase<E> of(String id, Stage stage, Supplier<? extends E> event) {
        return new Phase<>(id, stage, event, false);
    }

    public static <E> Phase<E> replayable(String id, Stage stage, Supplier<? extends E> event) {
        return new Phase<>(id, stage, event, true);
    }

    /** Runs before each {@link Pipeline#reload} re-post: undo what the previous post did. */
    public Phase<E> onReset(Runnable reset) {
        if (!replayable) throw new IllegalStateException(id + " is not replayable");
        resets.add(reset);
        return this;
    }

    /** Runs after every post of this phase (late handlers included), with that post's event. */
    public Phase<E> onPosted(java.util.function.Consumer<? super E> hook) {
        posted.add(hook);
        return this;
    }

    public String id() {
        return id;
    }

    public Stage stage() {
        return stage;
    }

    public boolean isReplayable() {
        return replayable;
    }

    E create() {
        return event.get();
    }

    @Override
    public String toString() {
        return id;
    }
}
