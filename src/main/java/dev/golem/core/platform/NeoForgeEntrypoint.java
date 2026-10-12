package dev.golem.core.platform;

//? if neoforge {
import dev.golem.core.GolemCore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(GolemCore.MOD_ID)
public class NeoForgeEntrypoint {

    /**
     * Golem Core's own mod event bus. Kept because some of what this library emulates is delivered by
     * NeoForge as a mod-bus event rather than a callable registry — item colours, for one — and the
     * emulation has to be able to subscribe from shared code that knows nothing about buses.
     */
    private static IEventBus modBus;

    public NeoForgeEntrypoint(IEventBus modBus) {
        NeoForgeEntrypoint.modBus = modBus;
        GolemCore.LOG.info("Golem Core loaded on {} for MC {}", GolemCore.loader(), GolemCore.MC_VERSION);
        dev.golem.core.registry.RegistryDump.install(); // dev only: GOLEM_REGISTRY_DUMP=<file>
        dev.golem.core.world.Physics.init();
        dev.golem.core.world.RecipeTables.init();
        dev.golem.core.data.DataFiles.init();
        if (Platform.isClient()) dev.golem.core.data.DataFiles.initClient();
        if (Platform.isClient()) dev.golem.core.world.RecipeTables.initClient();
        // Dev-only visual test harness; a no-op unless -Dgolem.harness.port is set on a client.
        if (Platform.isClient()) dev.golem.core.harness.Harness.init();
        // The GUI scene renderer must be registered before the game renderer builds its PiP list.
        if (Platform.isClient()) dev.golem.core.gui.GuiScenes.bootstrap();
        // Every Golem-based mod's registers and payloads, filled once by Golem Core (see ModEntries).
        modBus.addListener(NeoForgeEntrypoint::fillRegistry);
        modBus.addListener(dev.golem.core.net.GolemNetwork::flushRegistrations);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(ModEntries::runRegistered));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void fillRegistry(net.neoforged.neoforge.registries.RegisterEvent event) {
        dev.golem.core.registry.DeferredRegister.flushRegistry(event.getRegistryKey(), (registryKey, entryKey, value) ->
                event.register((net.minecraft.resources.ResourceKey) registryKey, dev.golem.core.Ids.keyId(entryKey),
                        (java.util.function.Supplier) value));
    }

    /** @throws IllegalStateException if called before the mod is constructed. */
    public static IEventBus modBus() {
        if (modBus == null) throw new IllegalStateException("Golem Core's mod bus is not available yet");
        return modBus;
    }
}
//?}
