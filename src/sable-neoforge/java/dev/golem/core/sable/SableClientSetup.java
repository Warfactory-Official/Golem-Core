package dev.golem.core.sable;

import dev.golem.core.GolemCore;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/** The client half of NeoForge's entry to the Sable integration; see {@link SableSetup}. */
@EventBusSubscriber(modid = GolemCore.MOD_ID, value = Dist.CLIENT)
public final class SableClientSetup {

    private SableClientSetup() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded("sable") || !ModList.get().isLoaded("sodium")) return;
        SableIntegration.clientSetupWithSodium(fluid -> {
            IClientFluidTypeExtensions textures = IClientFluidTypeExtensions.of(fluid);
            var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
            return List.of(atlas.apply(textures.getStillTexture()), atlas.apply(textures.getFlowingTexture()));
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent stage) -> {
            if (stage.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) SableIntegration.renderFrame();
        });
    }
}
