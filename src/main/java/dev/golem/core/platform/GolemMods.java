package dev.golem.core.platform;

import dev.golem.core.content.Phase;
import dev.golem.core.content.Pipeline;
import java.util.ArrayList;
import java.util.List;
//? if fabric {
/*import dev.golem.core.net.GolemNetwork;
import dev.golem.core.registry.DeferredRegister;
import dev.golem.core.registry.LivingAttributes;
*///?}

/** Boots {@link GolemMod}s. */
public final class GolemMods {

    private GolemMods() {}

    /** Core's own payloads and world services; once, before any mod's {@code init}. */
    public static void declareCoreServices() {
        dev.golem.core.content.SyncedIndex.declare();
        dev.golem.core.machine.StateSyncPayload.declare();
        dev.golem.core.graph.LevelNodeGraph.install();
    }

    /** Client half of {@link #declareCoreServices}. */
    public static void registerCoreClient() {
        dev.golem.core.client.SyncedIndexClient.register();
        dev.golem.core.machine.StateSyncPayload.registerClient();
    }

    private static final List<GolemMod> MODS = new ArrayList<>();
    //? if forgelike {
    private static final java.util.Map<String, GolemMod> CONSTRUCTED = new java.util.HashMap<>();

    /**
     * Forge-like: call from the mod's {@code @Mod} constructor; {@code init} is deferred to {@link #initConstructed}.
     * Forge 1.20.1 constructs mods in parallel => initialising here races dependents against dependencies.
     */
    public static synchronized void boot(GolemMod mod) {
        //? if neoforge {
        String id = net.neoforged.fml.ModLoadingContext.get().getActiveNamespace();
        //?} else {
        /*String id = net.minecraftforge.fml.ModLoadingContext.get().getActiveNamespace();
        *///?}
        if (CONSTRUCTED.putIfAbsent(id, mod) != null) throw new IllegalStateException("Two Golem mods booted as " + id);
    }

    /** Forge-like, first {@code RegisterEvent} on Golem Core's bus: constructed mods in the loader's dependency order. */
    static synchronized void initConstructed() {
        if (CONSTRUCTED.isEmpty()) return;
        //? if neoforge {
        var sorted = net.neoforged.fml.ModList.get().getMods();
        //?} else {
        /*var sorted = net.minecraftforge.fml.ModList.get().getMods();
        *///?}
        for (var info : sorted) {
            GolemMod mod = CONSTRUCTED.remove(info.getModId());
            if (mod == null) continue;
            MODS.add(mod);
            mod.init();
            if (Platform.isClient()) mod.initClient();
        }
        if (!CONSTRUCTED.isEmpty()) throw new IllegalStateException("Golem mods absent from the mod list: " + CONSTRUCTED.keySet());
        Pipeline.postStage(Phase.Stage.BEFORE_FLUSH);
    }
    //?}

    /** Forge-like common setup, from Golem Core's own bus. */
    static synchronized void setupAll() {
        MODS.forEach(GolemMod::setup);
        Pipeline.postStage(Phase.Stage.AFTER_SETUP);
    }

    //? if fabric {
    /*private static boolean fabricBooted;

    /^*
     * Every {@code golem} entrypoint, dependencies first; once. From Golem Core's {@code main}, or earlier from whatever
     * needs the mods declared first (fabric-gametest calls generators from its own, shuffled, {@code main}).
     ^/
    public static synchronized void fabricMain() {
        if (fabricBooted) return;
        fabricBooted = true;
        GolemMods.declareCoreServices();
        MODS.addAll(fabricMods());
        MODS.forEach(GolemMod::init);
        Pipeline.postStage(Phase.Stage.BEFORE_FLUSH);
        DeferredRegister.flushPendingToVanilla();
        LivingAttributes.flushFabric();
        GolemNetwork.flushCommonRegistrations();
        MODS.forEach(GolemMod::setup);
        Pipeline.postStage(Phase.Stage.AFTER_SETUP);
    }

    /^* Golem Core's Fabric {@code client}, after every {@code main}. ^/
    static synchronized void fabricClient() {
        registerCoreClient();
        MODS.forEach(GolemMod::initClient);
        GolemNetwork.flushClientRegistrations();
    }

    private static List<GolemMod> fabricMods() {
        var containers = net.fabricmc.loader.api.FabricLoader.getInstance().getEntrypointContainers("golem", GolemMod.class);
        java.util.Map<String, net.fabricmc.loader.api.entrypoint.EntrypointContainer<GolemMod>> byId = new java.util.LinkedHashMap<>();
        for (var container : containers) {
            String id = container.getProvider().getMetadata().getId();
            if (byId.putIfAbsent(id, container) != null) throw new IllegalStateException("Two golem entrypoints in " + id);
        }
        List<GolemMod> ordered = new ArrayList<>();
        java.util.Set<String> done = new java.util.HashSet<>(), visiting = new java.util.HashSet<>();
        for (String id : byId.keySet()) visit(id, byId, done, visiting, ordered);
        return ordered;
    }

    private static void visit(String id, java.util.Map<String, net.fabricmc.loader.api.entrypoint.EntrypointContainer<GolemMod>> byId,
            java.util.Set<String> done, java.util.Set<String> visiting, List<GolemMod> ordered) {
        if (done.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalStateException("Golem entrypoint dependency cycle at " + id);
        var container = byId.get(id);
        for (var dependency : container.getProvider().getMetadata().getDependencies()) {
            if (dependency.getKind() == net.fabricmc.loader.api.metadata.ModDependency.Kind.DEPENDS
                    && byId.containsKey(dependency.getModId())) {
                visit(dependency.getModId(), byId, done, visiting, ordered);
            }
        }
        visiting.remove(id);
        done.add(id);
        ordered.add(container.getEntrypoint());
    }
    *///?}
}
