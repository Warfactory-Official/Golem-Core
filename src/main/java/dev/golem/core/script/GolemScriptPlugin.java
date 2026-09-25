package dev.golem.core.script;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

//? if kubejs {
import dev.golem.core.Ids;
import dev.latvian.mods.kubejs.script.ScriptType;
//? if <1.21 {
/*import dev.latvian.mods.rhino.util.wrap.TypeWrappers;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.client.LangEventJS;
import dev.latvian.mods.kubejs.generator.AssetJsonGenerator;
import dev.latvian.mods.kubejs.generator.DataJsonGenerator;
import dev.latvian.mods.kubejs.script.BindingsEvent;
*///?} else {
import dev.latvian.mods.kubejs.client.LangKubeEvent;
import dev.latvian.mods.kubejs.generator.KubeAssetGenerator;
import dev.latvian.mods.kubejs.generator.KubeDataGenerator;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.TypeDescriptionRegistry;
import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.latvian.mods.rhino.util.wrap.DirectTypeWrapperFactory;

import java.util.concurrent.ConcurrentHashMap;
//?}
//?}

/**
 * A KubeJS plugin written once for KubeJS 6.x (1.20.1), 7.x (1.21.1) and 8.x (26.1).
 *
 * <p>Subclass it, implement {@link #declare}, and name the subclass in the mod's own
 * {@code kubejs.plugins.txt} as {@code com.example.MyScripts kubejs}. KubeJS instantiates plugins
 * before it evaluates any script, so what {@link #declare} states is in place whatever order the
 * loader constructs mods in. The subclass names no KubeJS type: a binding is any object whose public
 * methods scripts call, and a JS function passed to a functional-interface parameter arrives as that
 * interface. On a node built without KubeJS this is a plain class and nothing instantiates it.
 *
 * <p>Startup scripts run on both sides before any registry is flushed; server and client globals
 * are rebound on every load of their script type.
 */
public abstract class GolemScriptPlugin
        //? if kubejs {
        //? if <1.21 {
        /*extends KubeJSPlugin
        *///?} else {
        implements KubeJSPlugin
        //?}
        //?}
{

    /** A generated pack file. {@code path} is relative to the namespace root, without {@code .json}. */
    @FunctionalInterface
    public interface Resources {
        void json(String namespace, String path, JsonElement json);
    }

    /** An {@code en_us} entry. */
    @FunctionalInterface
    public interface Lang {
        void add(String namespace, String key, String value);
    }

    private record Wrapper<T>(Class<T> type, Function<Object, T> wrap, Class<?>[] describedAs) {}

    private final Map<String, Supplier<?>> startupBindings = new LinkedHashMap<>();
    private final Map<String, Supplier<?>> serverBindings = new LinkedHashMap<>();
    private final Map<String, Supplier<?>> clientBindings = new LinkedHashMap<>();
    private final List<Wrapper<?>> wrappers = new ArrayList<>();
    private final List<Runnable> startupLoaded = new ArrayList<>();
    private final List<Consumer<Resources>> assets = new ArrayList<>();
    private final List<Consumer<Resources>> data = new ArrayList<>();
    private final List<Consumer<Lang>> lang = new ArrayList<>();
    private boolean declared;

    /** States everything below. Runs once, before the first script load. */
    protected abstract void declare();

    /** A global in startup scripts; the supplier runs once per script load. */
    protected final void bindStartup(String name, Supplier<?> value) {
        if (startupBindings.putIfAbsent(name, value) != null) {
            throw new IllegalStateException("Startup binding " + name + " is already declared");
        }
    }

    /** A global in server scripts; the supplier runs once per server-script load. */
    protected final void bindServer(String name, Supplier<?> value) {
        if (serverBindings.putIfAbsent(name, value) != null) {
            throw new IllegalStateException("Server binding " + name + " is already declared");
        }
    }

    /** A global in client scripts; the supplier runs once per client-script load. */
    protected final void bindClient(String name, Supplier<?> value) {
        if (clientBindings.putIfAbsent(name, value) != null) {
            throw new IllegalStateException("Client binding " + name + " is already declared");
        }
    }

    /**
     * Converts any script value passed where a {@code type} parameter is declared. {@code describedAs}
     * is what editor typings (ProbeJS, KubeJS 7+) show the parameter as accepting besides {@code type}.
     */
    protected final <T> void typeWrapper(Class<T> type, Function<Object, T> wrap, Class<?>... describedAs) {
        wrappers.add(new Wrapper<>(type, wrap, describedAs));
    }

    /**
     * Runs after every evaluation of the startup scripts, on the thread that evaluated them: the launch,
     * and on KubeJS 7+ every {@code /kubejs reload startup_scripts} as well.
     */
    protected final void onStartupScriptsLoaded(Runnable hook) {
        startupLoaded.add(hook);
    }

    /** Files for KubeJS's generated client pack, asked for on every resource reload. */
    protected final void assets(Consumer<Resources> generator) {
        assets.add(generator);
    }

    /** Files for KubeJS's generated server data pack, asked for on every data reload. */
    protected final void data(Consumer<Resources> generator) {
        data.add(generator);
    }

    /** Entries for KubeJS's generated {@code en_us}. */
    protected final void lang(Consumer<Lang> generator) {
        lang.add(generator);
    }

    private synchronized void ensureDeclared() {
        if (!declared) {
            declared = true;
            declare();
        }
    }

    //? if kubejs {
    @Override
    public void init() {
        ensureDeclared();
    }

    private Map<String, Supplier<?>> bindingsFor(ScriptType type) {
        if (type == ScriptType.STARTUP) return startupBindings;
        if (type == ScriptType.SERVER) return serverBindings;
        return clientBindings;
    }

    //? if <1.21 {
    /*@Override
    public void initStartup() {
        ensureDeclared();
        startupLoaded.forEach(Runnable::run);
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        ensureDeclared();
        bindingsFor(event.getType()).forEach((name, value) -> event.add(name, value.get()));
    }

    @Override
    public void registerTypeWrappers(ScriptType type, TypeWrappers typeWrappers) {
        ensureDeclared();
        wrappers.forEach(wrapper -> register(typeWrappers, wrapper));
    }

    private static <T> void register(TypeWrappers typeWrappers, Wrapper<T> wrapper) {
        typeWrappers.registerSimple(wrapper.type(), wrapper.wrap()::apply);
    }

    @Override
    public void generateAssetJsons(AssetJsonGenerator generator) {
        ensureDeclared();
        assets.forEach(gen -> gen.accept((namespace, path, json) -> generator.json(Ids.of(namespace, path), json)));
    }

    @Override
    public void generateDataJsons(DataJsonGenerator generator) {
        ensureDeclared();
        data.forEach(gen -> gen.accept((namespace, path, json) -> generator.json(Ids.of(namespace, path), json)));
    }

    @Override
    public void generateLang(LangEventJS event) {
        ensureDeclared();
        lang.forEach(gen -> gen.accept(event::add));
    }
    *///?} else {
    private final Map<ScriptType, Map<String, Object>> bound = new ConcurrentHashMap<>();

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        bound.remove(manager.scriptType);
    }

    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        ensureDeclared();
        if (manager.scriptType == ScriptType.STARTUP) startupLoaded.forEach(Runnable::run);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        ensureDeclared();
        bound.computeIfAbsent(bindings.type(), type -> {
            Map<String, Object> values = new LinkedHashMap<>();
            bindingsFor(type).forEach((name, value) -> values.put(name, value.get()));
            return values;
        }).forEach(bindings::add);
    }

    @Override
    public void registerTypeWrappers(TypeWrapperRegistry registry) {
        ensureDeclared();
        wrappers.forEach(wrapper -> register(registry, wrapper));
    }

    private static <T> void register(TypeWrapperRegistry registry, Wrapper<T> wrapper) {
        registry.register(wrapper.type(), (DirectTypeWrapperFactory<T>) wrapper.wrap()::apply);
    }

    @Override
    public void registerTypeDescriptions(TypeDescriptionRegistry registry) {
        ensureDeclared();
        for (Wrapper<?> wrapper : wrappers) {
            TypeInfo info = TypeInfo.of(wrapper.type());
            for (Class<?> alt : wrapper.describedAs()) info = info.or(TypeInfo.of(alt));
            registry.register(wrapper.type(), info);
        }
    }

    @Override
    public void generateAssets(KubeAssetGenerator generator) {
        ensureDeclared();
        assets.forEach(gen -> gen.accept((namespace, path, json) -> generator.json(Ids.of(namespace, path), json)));
    }

    @Override
    public void generateData(KubeDataGenerator generator) {
        ensureDeclared();
        data.forEach(gen -> gen.accept((namespace, path, json) -> generator.json(Ids.of(namespace, path), json)));
    }

    @Override
    public void generateLang(LangKubeEvent event) {
        ensureDeclared();
        lang.forEach(gen -> gen.accept(event::add));
    }
    //?}
    //?}
}
