package dev.golem.core.platform;

//? if fabric {
/*import net.fabricmc.api.ClientModInitializer;

/^*
 * Golem Core's Fabric client entrypoint. Fabric runs client entrypoints in no particular order, so a mod's clientbound
 * payload handlers may be registered after another mod already installed its own; Golem Core installs whatever is
 * still missing once the client has started, before any connection exists.
 ^/
public final class FabricClientEntrypoint implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        //? if <1.21 {
        /^net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        // Chunk models (Disguises, connected textures) need a Fabric renderer under Sodium 0.5, which only Indium adds.
        // Indium is client-only, so this is checked here rather than in fabric.mod.json, which servers also read.
        if (!loader.isDevelopmentEnvironment() && !loader.isModLoaded("indium")) {
            throw new IllegalStateException("Golem Core on Minecraft 1.20.1 Fabric requires Indium (and Sodium): "
                    + "https://modrinth.com/mod/indium");
        }^/
        //?}
        dev.golem.core.world.RecipeTables.initClient();
        dev.golem.core.data.DataFiles.initClient();
        dev.golem.core.profiler.ProfilerCommands.initClient();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
                client -> dev.golem.core.net.GolemNetwork.flushClientRegistrations());
    }
}
*///?}
