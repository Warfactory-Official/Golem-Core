package dev.golem.core.sable;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric's entry to the Sable integration ({@link SableIntegration}). A {@code main} entrypoint listed in
 * {@code fabric.mod.json} only on nodes that declare Sable (the build appends it there), so the shared tree names none
 * of it.
 */
public final class SableFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("sable")) return;
        // Sable's Fabric build has no Flywheel embedding: instanced visuals on a sub-level would be drawn at its plot.
        SableIntegration.setup(false);
        ServerTickEvents.END_SERVER_TICK.register(server -> SableIntegration.endOfServerTick());
    }
}
