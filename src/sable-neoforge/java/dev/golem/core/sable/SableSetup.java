package dev.golem.core.sable;

import dev.golem.core.GolemCore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge's entry to the Sable integration ({@link SableIntegration}). Found by NeoForge's annotation scan, so the
 * shared tree names none of it: the integration is the {@code src/sable} roots alone.
 */
@EventBusSubscriber(modid = GolemCore.MOD_ID)
public final class SableSetup {

    private SableSetup() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded("sable")) return;
        // Sable's NeoForge build embeds Flywheel visuals in sub-levels (its compatibility.flywheel mixins).
        SableIntegration.setup(true);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post tick) -> SableIntegration.endOfServerTick());
    }
}
