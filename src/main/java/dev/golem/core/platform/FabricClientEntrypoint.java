package dev.golem.core.platform;

//? if fabric {
/*import net.fabricmc.api.ClientModInitializer;

/^* Runs every Golem mod's {@code initClient}; Fabric calls all {@code client} entrypoints after every {@code main}. ^/
public class FabricClientEntrypoint implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GolemMods.fabricClient();
    }
}
*///?}
