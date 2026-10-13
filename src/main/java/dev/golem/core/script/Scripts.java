package dev.golem.core.script;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What a mod offers to scripts (KubeJS), stated once and with no dependency on any scripting mod: global bindings,
 * observe-only {@link ScriptEvent}s and work to run once startup scripts are loaded. Golem Core's scripting adapter
 * (one per KubeJS API line, on the nodes KubeJS exists for) reads these; on a node or install without one, every call
 * here is a cheap no-op. Machine recipe tables need no declaration here: every
 * {@link dev.golem.core.world.RecipeTables.Table} becomes a script recipe schema from its
 * {@link dev.golem.core.world.RecipeFields}.
 *
 * <p>Declare at mod construction (a mod's {@code declare()}), like every other Golem Core declaration.
 *
 * <pre>{@code
 * Scripts.binding("HBM", () -> HbmScriptApi.INSTANCE);           // scripts: HBM.radiation(player)
 * static final Scripts.Group EVENTS = Scripts.group("HBMEvents");  // scripts: HBMEvents.nuclearBlast(e => ...)
 * }</pre>
 */
public final class Scripts {

    static final Logger LOGGER = LoggerFactory.getLogger("Golem Core scripts");

    /** A global binding: the name scripts use and how to get the object (asked each time scripts load). */
    public record Binding(String name, Supplier<?> value) {}

    private static final Map<String, Binding> BINDINGS = new LinkedHashMap<>();
    private static final Map<String, Group> GROUPS = new LinkedHashMap<>();
    private static final List<Runnable> STARTUP = new ArrayList<>();
    private static volatile Dispatcher dispatcher;
    private static volatile boolean startupLoaded;

    private Scripts() {}

    /** Adds the global {@code name} to every script (startup, server and client). Names are unique across mods. */
    public static synchronized void binding(String name, Supplier<?> value) {
        Objects.requireNonNull(value);
        if (BINDINGS.putIfAbsent(name, new Binding(name, value)) != null) {
            throw new IllegalStateException("Script binding " + name + " declared twice");
        }
    }

    /** The event group {@code name} (a script global, e.g. {@code HBMEvents}); the same group for the same name. */
    public static synchronized Group group(String name) {
        return GROUPS.computeIfAbsent(name, Group::new);
    }

    /**
     * Runs {@code work} once, right after the scripting mod has run its startup scripts (so {@link ScriptEvent.Side#STARTUP}
     * events posted there reach them), before any datapack is loaded. Never runs where no adapter is installed.
     */
    public static synchronized void onStartupScriptsLoaded(Runnable work) {
        if (startupLoaded) {
            work.run();
        } else {
            STARTUP.add(work);
        }
    }

    /** Whether a scripting adapter is installed in this game (KubeJS is present on this node). */
    public static boolean available() {
        return dispatcher != null;
    }

    // ----- adapter side -------------------------------------------------------------------------------------------

    /** What a scripting adapter implements. Not for mods. */
    public interface Dispatcher {
        boolean hasListeners(ScriptEvent<?> event);

        void post(ScriptEvent<?> event, Object payload);
    }

    /** Installs the adapter (once, when the scripting mod initialises its plugins). Not for mods. */
    public static synchronized void install(Dispatcher adapter) {
        if (dispatcher != null) throw new IllegalStateException("A script adapter is already installed");
        dispatcher = Objects.requireNonNull(adapter);
        for (Group group : GROUPS.values()) {
            for (ScriptEvent<?> event : group.events()) event.dispatcher = adapter;
        }
    }

    /** The adapter calls this once the startup scripts ran. Not for mods. */
    public static void startupScriptsLoaded() {
        List<Runnable> work;
        synchronized (Scripts.class) {
            if (startupLoaded) return;
            startupLoaded = true;
            work = List.copyOf(STARTUP);
            STARTUP.clear();
        }
        for (Runnable runnable : work) {
            try {
                runnable.run();
            } catch (RuntimeException e) {
                LOGGER.error("Work after startup scripts failed", e);
            }
        }
    }

    public static synchronized Collection<Binding> bindings() {
        return List.copyOf(BINDINGS.values());
    }

    public static synchronized Collection<Group> groups() {
        return List.copyOf(GROUPS.values());
    }

    /** A script event group: one global name, many events. */
    public static final class Group {

        private final String name;
        private final Map<String, ScriptEvent<?>> events = new LinkedHashMap<>();

        private Group(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        /** An event posted on the logical server, for server scripts. */
        public <T> ScriptEvent<T> server(String event, Class<T> payload) {
            return event(event, ScriptEvent.Side.SERVER, payload);
        }

        /** An event posted once during start-up, for startup scripts. */
        public <T> ScriptEvent<T> startup(String event, Class<T> payload) {
            return event(event, ScriptEvent.Side.STARTUP, payload);
        }

        /** An event posted on the client, for client scripts. */
        public <T> ScriptEvent<T> client(String event, Class<T> payload) {
            return event(event, ScriptEvent.Side.CLIENT, payload);
        }

        /** An event posted on either side. */
        public <T> ScriptEvent<T> common(String event, Class<T> payload) {
            return event(event, ScriptEvent.Side.COMMON, payload);
        }

        public synchronized <T> ScriptEvent<T> event(String event, ScriptEvent.Side side, Class<T> payload) {
            ScriptEvent<T> created = new ScriptEvent<>(name, event, side, payload);
            if (events.putIfAbsent(event, created) != null) {
                throw new IllegalStateException("Script event " + name + "." + event + " declared twice");
            }
            created.dispatcher = dispatcher;
            return created;
        }

        public synchronized Collection<ScriptEvent<?>> events() {
            return List.copyOf(events.values());
        }
    }
}
