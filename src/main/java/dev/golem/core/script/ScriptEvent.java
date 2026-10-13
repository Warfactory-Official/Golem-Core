package dev.golem.core.script;

import java.util.Objects;

/**
 * One script event a mod offers, observe-only: scripts (KubeJS, where it is installed) are told that something happened
 * and read the payload, but nothing they do changes what the mod does. Declare it once, in a static field, and post at
 * the moment itself:
 *
 * <pre>{@code
 * public static final ScriptEvent<NuclearBlast> NUCLEAR_BLAST = Scripts.group("HBMEvents").server("nuclearBlast", NuclearBlast.class);
 * if (NUCLEAR_BLAST.active()) NUCLEAR_BLAST.post(new NuclearBlast(level, x, y, z, radius));
 * }</pre>
 *
 * <p>The script sees the payload's public getters and record accessors as properties of the event object
 * ({@code event.radius}), the same on every node that has a scripting adapter. A payload should be a small immutable
 * record of plain values and game objects (level, entity, position, ids); never hand a script a live mod-internal
 * object it could change.
 *
 * <p>{@link #post} is free when no adapter is installed or no script listens ({@link #active()}), and never throws: a
 * failing script handler is reported by the scripting mod and the mod carries on.
 *
 * @param <T> the payload type
 */
public final class ScriptEvent<T> {

    /** When the scripts that may listen run. */
    public enum Side {
        /** Startup scripts: posted once, while the game starts ({@link Scripts#onStartupScriptsLoaded}). */
        STARTUP,
        /** Server scripts: posted on the logical server, reloaded with {@code /reload}. */
        SERVER,
        /** Client scripts: posted on the client. */
        CLIENT,
        /** Posted on both logical sides; server and client scripts may both listen. */
        COMMON
    }

    private final String group;
    private final String name;
    private final Side side;
    private final Class<T> payload;
    volatile Scripts.Dispatcher dispatcher;

    ScriptEvent(String group, String name, Side side, Class<T> payload) {
        this.group = group;
        this.name = name;
        this.side = side;
        this.payload = Objects.requireNonNull(payload);
    }

    /** The event group's script name, e.g. {@code HBMEvents}. */
    public String group() {
        return group;
    }

    /** The event's name within its group, e.g. {@code nuclearBlast}. */
    public String name() {
        return name;
    }

    public Side side() {
        return side;
    }

    public Class<T> payloadType() {
        return payload;
    }

    /** Whether posting could reach any script now; check it before building an expensive payload. */
    public boolean active() {
        Scripts.Dispatcher current = dispatcher;
        return current != null && current.hasListeners(this);
    }

    /** Tells listening scripts; the payload is read-only to them. */
    public void post(T value) {
        Scripts.Dispatcher current = dispatcher;
        if (current == null || !current.hasListeners(this)) return;
        try {
            current.post(this, value);
        } catch (RuntimeException e) {
            Scripts.LOGGER.error("Script event {}.{} failed", group, name, e);
        }
    }

    @Override
    public String toString() {
        return group + "." + name;
    }
}
