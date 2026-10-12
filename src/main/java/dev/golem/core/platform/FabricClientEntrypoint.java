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
        dev.golem.core.world.RecipeTables.initClient();
        dev.golem.core.data.DataFiles.initClient();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
                client -> dev.golem.core.net.GolemNetwork.flushClientRegistrations());
    }
}
*///?}
