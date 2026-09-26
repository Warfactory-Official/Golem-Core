package dev.golem.core.platform;

//? if neoforge {
import dev.golem.core.GolemCore;
import dev.golem.core.gametest.GameTests;
import dev.golem.core.net.GolemNetwork;
import dev.golem.core.registry.DeferredRegister;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

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
        GolemMods.declareCoreServices();
        if (Platform.isClient()) GolemMods.registerCoreClient();
        modBus.addListener(NeoForgeEntrypoint::onRegister);
        modBus.addListener(GolemNetwork::flushRegistrations);
        modBus.addListener(GameTests::registerAll);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(GolemMods::setupAll));
        // Dev-only visual test harness; a no-op unless -Dgolem.harness.port is set on a client.
        if (Platform.isClient()) dev.golem.core.harness.Harness.init();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onRegister(RegisterEvent event) {
        GolemMods.initConstructed();
        DeferredRegister.flushPending(event.getRegistryKey(), (registryKey, entryKey, value) ->
                event.register((ResourceKey) registryKey, dev.golem.core.Ids.keyId(entryKey), (Supplier) value));
    }

    /** @throws IllegalStateException if called before the mod is constructed. */
    public static IEventBus modBus() {
        if (modBus == null) throw new IllegalStateException("Golem Core's mod bus is not available yet");
        return modBus;
    }
}
//?}
