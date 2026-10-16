package dev.golem.core.platform;

//? if forge {
/*import dev.golem.core.GolemCore;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(GolemCore.MOD_ID)
public final class ForgeEntrypoint {

    private static IEventBus modBus;

    public ForgeEntrypoint() {
        modBus = FMLJavaModLoadingContext.get().getModEventBus();
        GolemCore.LOG.info("Golem Core loaded on {} for MC {}", GolemCore.loader(), GolemCore.MC_VERSION);
        dev.golem.core.registry.RegistryDump.install(); // dev only: GOLEM_REGISTRY_DUMP=<file>
        dev.golem.core.world.Physics.init();
        dev.golem.core.world.RecipeTables.init();
        dev.golem.core.data.DataFiles.init();
        if (Platform.isClient()) dev.golem.core.data.DataFiles.initClient();
        if (Platform.isClient()) dev.golem.core.world.RecipeTables.initClient();
        // Every Golem-based mod's registers and payloads, filled once by Golem Core (see ModEntries). Payloads wait
        // for common setup, after every mod is constructed: Forge numbers them as they are registered.
        modBus.addListener(ForgeEntrypoint::fillRegistry);
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(() -> {
                    dev.golem.core.net.GolemNetwork.flushCommonRegistrations();
                    ModEntries.runRegistered();
                }));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void fillRegistry(net.minecraftforge.registries.RegisterEvent event) {
        dev.golem.core.registry.DeferredRegister.flushRegistry(event.getRegistryKey(), (registryKey, entryKey, value) ->
                event.register((net.minecraft.resources.ResourceKey) registryKey, dev.golem.core.Ids.keyId(entryKey),
                        (java.util.function.Supplier) value));
    }

    public static IEventBus modBus() {
        if (modBus == null) throw new IllegalStateException("Golem Core's mod bus is not available yet");
        return modBus;
    }
}
*///?}
