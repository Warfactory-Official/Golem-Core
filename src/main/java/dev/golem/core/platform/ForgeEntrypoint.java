package dev.golem.core.platform;

//? if forge {
/*import dev.golem.core.GolemCore;
import dev.golem.core.gametest.GameTests;
import dev.golem.core.net.GolemNetwork;
import dev.golem.core.registry.DeferredRegister;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(GolemCore.MOD_ID)
public final class ForgeEntrypoint {

    private static IEventBus modBus;

    public ForgeEntrypoint() {
        modBus = FMLJavaModLoadingContext.get().getModEventBus();
        GolemCore.LOG.info("Golem Core loaded on {} for MC {}", GolemCore.loader(), GolemCore.MC_VERSION);
        GolemMods.declareCoreServices();
        if (Platform.isClient()) GolemMods.registerCoreClient();
        modBus.addListener(ForgeEntrypoint::onRegister);
        modBus.addListener(GameTests::registerAll);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            GolemNetwork.flushCommonRegistrations();
            GolemMods.setupAll();
        }));
        if (Platform.isClient()) {
            modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) ->
                    GolemNetwork.flushClientRegistrations());
        }
        // Dev-only visual test harness; a no-op unless -Dgolem.harness.port is set on a client.
        if (Platform.isClient()) dev.golem.core.harness.Harness.init();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onRegister(RegisterEvent event) {
        GolemMods.initConstructed();
        DeferredRegister.flushPending(event.getRegistryKey(), (registryKey, entryKey, value) ->
                event.register((ResourceKey) registryKey, dev.golem.core.Ids.keyId(entryKey), (Supplier) value));
    }

    public static IEventBus modBus() {
        if (modBus == null) throw new IllegalStateException("Golem Core's mod bus is not available yet");
        return modBus;
    }
}
*///?}
