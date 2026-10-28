package dev.golem.core.platform;

import dev.golem.core.client.UnbakedModels;

//? if fabric {
/*import dev.golem.core.GolemCore;
import net.fabricmc.api.ModInitializer;

public class FabricEntrypoint implements ModInitializer {

    private static final java.util.concurrent.atomic.AtomicBoolean INITIALIZED = new java.util.concurrent.atomic.AtomicBoolean();

    @Override
    public void onInitialize() {
        initialize();
    }

    /^*
     * Golem Core's main initialisation, once: from this entrypoint, or earlier from a mod that needs every Golem-based
     * mod declared before its own main entrypoint finishes (Golem Core's KubeJS plugin, through
     * {@link ModEntries#ensureDeclared}); Fabric runs main entrypoints in no set order.
     ^/
    static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        if (Platform.isClient()) UnbakedModels.initialize();
        GolemCore.LOG.info("Golem Core loaded on {} for MC {}", GolemCore.loader(), GolemCore.MC_VERSION);
        dev.golem.core.registry.RegistryDump.install(); // dev only: GOLEM_REGISTRY_DUMP=<file>
        dev.golem.core.world.Physics.init();
        dev.golem.core.world.RecipeTables.init();
        dev.golem.core.world.LootCounts.init();
        dev.golem.core.data.DataFiles.init();
        // The GUI scene renderer must be registered before the game renderer builds its PiP list.
        if (Platform.isClient()) dev.golem.core.gui.GuiScenes.bootstrap();
        // Every Golem-based mod's golemcore:entry, in dependency order, and the registries filled once.
        ModEntries.runFabric();
    }
}
*///?}
