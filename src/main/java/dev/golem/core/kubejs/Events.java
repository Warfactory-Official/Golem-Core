package dev.golem.core.kubejs;

//? if kubejs {

import dev.golem.core.script.ScriptEvent;
import dev.golem.core.script.Scripts;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupWrapper;
import dev.latvian.mods.kubejs.event.EventHandler;
//? if >=1.20.5 {
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;
//?} else {
/*import dev.latvian.mods.kubejs.script.BindingsEvent;
*///?}
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.ScriptTypeHolder;
import dev.latvian.mods.kubejs.script.ScriptTypePredicate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Scripts}' groups and events as KubeJS event groups and handlers, and the {@link Scripts.Dispatcher} that posts
 * to them. One KubeJS group per {@link Scripts.Group} (its name is the script global, e.g. {@code HBMEvents}), one
 * handler per {@link ScriptEvent}, for the script types of its side; the event object is a {@link PayloadEvent}.
 *
 * <p>A mod may declare its groups after KubeJS asked plugins for theirs (NeoForge constructs mods in dependency
 * order, and a Golem-based mod does not depend on KubeJS): those groups are still created on first use and bound by
 * {@link #bind} for every later script load (server and client scripts), with a WARN ({@link #late}). Startup
 * scripts load inside KubeJS's own constructor, so they see a group only if its mod was constructed first.
 */
final class Events implements Scripts.Dispatcher {

    static final Events INSTANCE = new Events();
    private static final Logger LOGGER = LoggerFactory.getLogger("Golem Core scripts");

    private final Map<String, EventGroup> groups = new ConcurrentHashMap<>();
    private final Map<ScriptEvent<?>, EventHandler> handlers = new ConcurrentHashMap<>();
    private final java.util.Set<String> early = ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> warned = ConcurrentHashMap.newKeySet();
    private volatile boolean registered;

    private Events() {}

    /** Installs the dispatcher once (a second KubeJS plugin init, e.g. a startup-script reload, is ignored). */
    static void install() {
        if (!Scripts.available()) Scripts.install(INSTANCE);
    }

    /** KubeJS's registerEvents: every group declared so far. */
    //? if >=1.20.5 {
    void register(EventGroupRegistry registry) {
        for (Scripts.Group group : Scripts.groups()) {
            registry.register(group(group));
    //?} else {
    /*void register() {
        for (Scripts.Group group : Scripts.groups()) {
            group(group).register(); // KubeJS 6: a global map that its own plugin binds from
    *///?}
            early.add("group " + group.name());
        }
        for (Scripts.Binding binding : Scripts.bindings()) early.add("binding " + binding.name());
        registered = true;
    }

    /** KubeJS's registerBindings: every group (including ones declared after {@link #register}) and every binding. */
    //? if >=1.20.5 {
    void bind(BindingRegistry bindings) {
        ScriptType type = bindings.type();
    //?} else {
    /*void bind(BindingsEvent bindings) {
        ScriptType type = bindings.getType();
    *///?}
        for (Scripts.Group group : Scripts.groups()) {
            late("group " + group.name());
            bindings.add(group.name(), new EventGroupWrapper(type, group(group)));
        }
        for (Scripts.Binding binding : Scripts.bindings()) {
            late("binding " + binding.name());
            try {
                bindings.add(binding.name(), binding.value().get());
            } catch (RuntimeException e) {
                LOGGER.error("Script binding {} failed", binding.name(), e);
            }
        }
    }

    /**
     * The safety net for load order: a group or binding first seen after KubeJS asked for events was declared by a mod
     * constructed after KubeJS. Server and client scripts still get it ({@link #bind}); startup scripts never did.
     * Golem's build layer orders every Golem-based mod before KubeJS ({@code kubejs-order.gradle.kts}), so this
     * names a mod whose metadata missed that.
     */
    private void late(String what) {
        if (registered && !early.contains(what) && warned.add(what)) {
            LOGGER.warn("Script {} was declared after KubeJS loaded its plugins: startup scripts cannot see it "
                    + "(is its mod ordered BEFORE kubejs?)", what);
        }
    }

    private EventGroup group(Scripts.Group declared) {
        EventGroup group = groups.computeIfAbsent(declared.name(), EventGroup::of);
        for (ScriptEvent<?> event : declared.events()) handler(event, group);
        return group;
    }

    private EventHandler handler(ScriptEvent<?> event) {
        EventHandler known = handlers.get(event);
        if (known != null) return known;
        return handler(event, groups.computeIfAbsent(event.group(), EventGroup::of));
    }

    private EventHandler handler(ScriptEvent<?> event, EventGroup group) {
        return handlers.computeIfAbsent(event, e -> {
            synchronized (group) {
                EventHandler existing = group.getHandlers().get(e.name());
                return existing != null ? existing : group.add(e.name(), predicate(e.side()), () -> PayloadEvent.class);
            }
        });
    }

    private static ScriptTypePredicate predicate(ScriptEvent.Side side) {
        return switch (side) {
            case STARTUP -> ScriptType.STARTUP;
            case SERVER -> ScriptType.SERVER;
            case CLIENT -> ScriptType.CLIENT;
            case COMMON -> ScriptTypePredicate.COMMON;
        };
    }

    @Override
    public boolean hasListeners(ScriptEvent<?> event) {
        return handler(event).hasListeners();
    }

    @Override
    public void post(ScriptEvent<?> event, Object payload) {
        EventHandler handler = handler(event);
        if (!handler.hasListeners()) return;
        PayloadEvent posted = new PayloadEvent(payload);
        handler.post(scriptType(event.side(), posted), posted);
    }

    /**
     * The script type a post goes to. A {@link ScriptEvent.Side#COMMON} event goes to the side of the first level or
     * entity among its properties (KubeJS gives both its script type), and to the server when it carries neither.
     */
    private static ScriptType scriptType(ScriptEvent.Side side, PayloadEvent posted) {
        return switch (side) {
            case STARTUP -> ScriptType.STARTUP;
            case SERVER -> ScriptType.SERVER;
            case CLIENT -> ScriptType.CLIENT;
            case COMMON -> {
                for (Object value : posted.properties().values()) {
                    if (value instanceof ScriptTypeHolder holder) yield holder.kjs$getScriptType();
                }
                yield ScriptType.SERVER;
            }
        };
    }
}
//?}
