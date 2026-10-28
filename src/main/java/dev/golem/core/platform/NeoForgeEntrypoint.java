package dev.golem.core.platform;

import dev.golem.core.client.UnbakedModels;
import dev.golem.core.net.Configurations;
//? if neoforge {
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
//?}

//? if neoforge {
import dev.golem.core.GolemCore;
import dev.golem.core.registry.ComponentPatches;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(GolemCore.MOD_ID)
public class NeoForgeEntrypoint {

    /**
     * Golem Core's own mod event bus. Kept because some of what this library emulates is delivered by
     * NeoForge as a mod-bus event rather than a callable registry (item colours, for one) and the
     * emulation has to be able to subscribe from shared code that knows nothing about buses.
     */
    private static IEventBus modBus;

    public NeoForgeEntrypoint(IEventBus modBus) {
        NeoForgeEntrypoint.modBus = modBus;
        GolemCore.LOG.info("Golem Core loaded on {} for MC {}", GolemCore.loader(), GolemCore.MC_VERSION);
        dev.golem.core.registry.RegistryDump.install(); // dev only: GOLEM_REGISTRY_DUMP=<file>
        dev.golem.core.world.Physics.init();
        dev.golem.core.world.RecipeTables.init();
        dev.golem.core.world.LootCounts.init();
        dev.golem.core.data.DataFiles.init();
        if (Platform.isClient()) UnbakedModels.initialize();
        if (Platform.isClient()) dev.golem.core.data.DataFiles.initClient();
        if (Platform.isClient()) dev.golem.core.world.RecipeTables.initClient();
        // The GUI scene renderer must be registered before the game renderer builds its PiP list.
        if (Platform.isClient()) dev.golem.core.gui.GuiScenes.bootstrap();
        // Every Golem-based mod's registers and payloads, filled once by Golem Core (see ModEntries).
        modBus.addListener(NeoForgeEntrypoint::fillRegistry);
        modBus.addListener(dev.golem.core.net.GolemNetwork::flushRegistrations);
        modBus.addListener(NeoForgeEntrypoint::modifyComponents);
        //? if >=1.20.5 {
        modBus.addListener(Configurations::registerTasks);
        //?}
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(ModEntries::runRegistered));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void fillRegistry(net.neoforged.neoforge.registries.RegisterEvent event) {
        dev.golem.core.registry.DeferredRegister.flushRegistry(event.getRegistryKey(), (registryKey, entryKey, value) ->
                event.register((net.minecraft.resources.ResourceKey) registryKey, dev.golem.core.Ids.keyId(entryKey),
                        (java.util.function.Supplier) value));
    }

    /** Applies queued {@link ComponentPatches} when the loader builds item components. */
    private static void modifyComponents(
            ModifyDefaultComponentsEvent event) {
        for (var patch : ComponentPatches.all()) {
            //? if >=26.1 {
            event.modify(patch.getKey().get(), (components, context, item) -> patch.getValue().accept(components));
            //?} else {
            /*event.modify(patch.getKey().get(), patchBuilder -> {
                DataComponentMap.Builder builder = DataComponentMap.builder();
                patch.getValue().accept(builder);
                DataComponentMap built = builder.build();
                for (var type : built.keySet()) copyComponent(built, patchBuilder, type);
            });
            *///?}
        }
    }

    //? if >=26.1 {
    //?} else {
    /*// Copies one set entry into a patch builder (1.21.1 takes patches, not maps);
    // set-only, which is all a default-component patch needs.
    private static <T> void copyComponent(DataComponentMap map,
            DataComponentPatch.Builder builder,
            DataComponentType<T> type) {
        T value = map.get(type);
        if (value != null) builder.set(type, value);
    }
    *///?}

    /** @throws IllegalStateException if called before the mod is constructed. */
    public static IEventBus modBus() {
        if (modBus == null) throw new IllegalStateException("Golem Core's mod bus is not available yet");
        return modBus;
    }
}
//?}
