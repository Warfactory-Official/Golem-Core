package dev.golem.core.platform;

import dev.golem.core.GolemCore;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every Golem-based mod's {@link ModEntry} the same way on every loader, and fills the registries once for all of
 * them. A mod never flushes registers or payloads itself: when one mod flushed "everything", a second Golem-based mod
 * was registered twice.
 *
 * <p><b>NeoForge and Forge</b> construct every mod before any registry is filled, so a mod calls {@link #start} from
 * its {@code @Mod} constructor: its substitutions and declarations run at once, Golem Core fills each registry from
 * its own {@code RegisterEvent} listener (every mod's registers for that registry), and {@link ModEntry#registered}
 * runs in Golem Core's common setup, after every mod's parallel setup work.
 *
 * <p><b>Fabric</b> has no deferred registration and does not run mods' {@code main} entrypoints in dependency order,
 * so a mod lists its entry under {@link #FABRIC_ENTRYPOINT} instead of {@code main}. Golem Core's own {@code main}
 * entrypoint runs every mod's substitutions, then every mod's declarations (dependencies first), fills the
 * registries, entity attributes, spawn placements and payloads once, then runs every {@code registered}.
 */
public final class ModEntries {

    /** The Fabric entrypoint a Golem-based mod lists its {@link ModEntry} under, in place of {@code main}. */
    public static final String FABRIC_ENTRYPOINT = "golemcore:entry";

    private static final List<ModEntry> STARTED = new ArrayList<>(); // guarded by itself
    private static boolean registeredRan; // guarded by STARTED

    private ModEntries() {}

    /**
     * NeoForge and Forge: call once from the mod's {@code @Mod} constructor. On Fabric list the class under
     * {@link #FABRIC_ENTRYPOINT} instead; calling this there is an error.
     */
    public static void start(ModEntry entry) {
        //? if fabric {
        /*throw new IllegalStateException("On Fabric, list " + entry.getClass().getName() + " under the \""
                + FABRIC_ENTRYPOINT + "\" entrypoint instead of calling ModEntries.start");
        *///?} else if forge {
        /*String modId = net.minecraftforge.fml.ModLoadingContext.get().getActiveNamespace();
        awaitGolemDependencies(modId);
        try {
            entry.substitute();
            entry.declare();
        } finally {
            DECLARED.computeIfAbsent(modId, id -> new java.util.concurrent.CountDownLatch(1)).countDown();
        }
        addStarted(entry);
        *///?} else {
        entry.substitute();
        entry.declare();
        addStarted(entry);
        //?}
    }

    private static void addStarted(ModEntry entry) {
        synchronized (STARTED) {
            if (registeredRan) {
                throw new IllegalStateException(entry.getClass().getName() + " was started after common setup");
            }
            STARTED.add(entry);
        }
    }

    //? if forge {
    /*/^*
     * Forge 1.20.1 constructs mods in parallel on its loading pool ({@code modloading-worker-N}); a mod's
     * {@code ordering = "AFTER"} only sorts the list it starts from. NeoForge constructs a mod after its
     * dependencies, which is what every Golem-based mod is written for: an addon's declarations add to its parent's
     * tables (fluids, siren tracks) and must come after the parent's. Here a Golem-based mod's {@link #start} waits
     * for the started declarations of every Golem-based mod it orders itself AFTER, so Forge declares in NeoForge's
     * order. Each mod counts down its latch even when its declarations throw, so a dependant fails on that error
     * rather than hanging; a dependency that never starts (its constructor threw first) times out.
     ^/
    private static final java.util.Map<String, java.util.concurrent.CountDownLatch> DECLARED =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static void awaitGolemDependencies(String modId) {
        var self = net.minecraftforge.fml.ModList.get().getModContainerById(modId);
        if (self.isEmpty()) return;
        for (var dependency : self.get().getModInfo().getDependencies()) {
            if (dependency.getOrdering() != net.minecraftforge.forgespi.language.IModInfo.Ordering.AFTER) continue;
            String other = dependency.getModId();
            if (other.equals(GolemCore.MOD_ID) || !isGolemBased(other)) continue;
            java.util.concurrent.CountDownLatch latch =
                    DECLARED.computeIfAbsent(other, id -> new java.util.concurrent.CountDownLatch(1));
            try {
                // a managed block, so the loading pool adds a worker while this one waits
                java.util.concurrent.ForkJoinPool.managedBlock(new java.util.concurrent.ForkJoinPool.ManagedBlocker() {
                    private boolean timedOut;

                    @Override
                    public boolean block() throws InterruptedException {
                        timedOut = !latch.await(5, java.util.concurrent.TimeUnit.MINUTES);
                        if (timedOut) {
                            throw new IllegalStateException(modId + " waited five minutes for " + other
                                    + " to declare its content; did " + other + "'s constructor fail?");
                        }
                        return true;
                    }

                    @Override
                    public boolean isReleasable() {
                        return latch.getCount() == 0;
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(modId + " was interrupted waiting for " + other, e);
            }
        }
    }

    /^* Whether {@code modId} is loaded and built on Golem Core (it depends on it), so it calls {@link #start}. ^/
    private static boolean isGolemBased(String modId) {
        var container = net.minecraftforge.fml.ModList.get().getModContainerById(modId);
        if (container.isEmpty()) return false;
        for (var dependency : container.get().getModInfo().getDependencies()) {
            if (dependency.getModId().equals(GolemCore.MOD_ID)) return true;
        }
        return false;
    }
    *///?}

    /** Golem Core's common setup on NeoForge and Forge: every started entry's {@link ModEntry#registered}. */
    static void runRegistered() {
        List<ModEntry> entries;
        synchronized (STARTED) {
            registeredRan = true;
            entries = List.copyOf(STARTED);
        }
        dev.golem.core.registry.Substitutions.reportUnmatched();
        for (ModEntry entry : entries) entry.registered();
        GolemCore.LOG.info("Mod entries registered: {}", names(entries));
    }

    private static List<String> names(List<ModEntry> entries) {
        List<String> names = new ArrayList<>();
        for (ModEntry entry : entries) names.add(entry.getClass().getName());
        return names;
    }

    //? if fabric {
    /*static void runFabric() {
        List<ModEntry> entries = fabricEntries();
        for (ModEntry entry : entries) entry.substitute();
        for (ModEntry entry : entries) entry.declare();
        dev.golem.core.registry.DeferredRegister.flushAllToVanilla();
        dev.golem.core.registry.LivingAttributes.flushFabric();
        dev.golem.core.world.Spawns.flushFabricPlacements();
        dev.golem.core.net.GolemNetwork.flushCommonRegistrations();
        dev.golem.core.registry.Substitutions.reportUnmatched();
        for (ModEntry entry : entries) entry.registered();
        GolemCore.LOG.info("Mod entries registered: {}", names(entries));
    }

    private static List<ModEntry> fabricEntries() {
        var containers = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getEntrypointContainers(FABRIC_ENTRYPOINT, ModEntry.class);
        java.util.Map<String, List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<ModEntry>>> byMod =
                new java.util.HashMap<>();
        java.util.Map<String, net.fabricmc.loader.api.metadata.ModMetadata> mods = new java.util.HashMap<>();
        for (var container : containers) {
            var metadata = container.getProvider().getMetadata();
            mods.put(metadata.getId(), metadata);
            byMod.computeIfAbsent(metadata.getId(), id -> new ArrayList<>()).add(container);
        }
        List<ModEntry> entries = new ArrayList<>();
        for (String id : dependencyOrder(mods)) {
            for (var container : byMod.get(id)) entries.add(container.getEntrypoint());
        }
        return entries;
    }

    // Each mod after the mods it depends on (required or optional, when present); among mods with nothing left to
    // wait for, alphabetical, so every run starts mods in the same order.
    private static List<String> dependencyOrder(java.util.Map<String, net.fabricmc.loader.api.metadata.ModMetadata> mods) {
        java.util.Map<String, java.util.Set<String>> waitsFor = new java.util.TreeMap<>();
        for (var mod : mods.entrySet()) {
            java.util.Set<String> dependencies = new java.util.TreeSet<>();
            for (var dependency : mod.getValue().getDependencies()) {
                var kind = dependency.getKind();
                boolean ordering = kind == net.fabricmc.loader.api.metadata.ModDependency.Kind.DEPENDS
                        || kind == net.fabricmc.loader.api.metadata.ModDependency.Kind.RECOMMENDS
                        || kind == net.fabricmc.loader.api.metadata.ModDependency.Kind.SUGGESTS;
                String other = dependency.getModId();
                if (ordering && mods.containsKey(other) && !other.equals(mod.getKey())) dependencies.add(other);
            }
            waitsFor.put(mod.getKey(), dependencies);
        }
        List<String> order = new ArrayList<>();
        while (!waitsFor.isEmpty()) {
            String next = null;
            for (var mod : waitsFor.entrySet()) {
                if (order.containsAll(mod.getValue())) {
                    next = mod.getKey();
                    break;
                }
            }
            if (next == null) {
                next = waitsFor.keySet().iterator().next();
                GolemCore.LOG.error("Mod entries of {} depend on each other; starting {} first", waitsFor.keySet(), next);
            }
            order.add(next);
            waitsFor.remove(next);
        }
        return order;
    }
    *///?}
}
