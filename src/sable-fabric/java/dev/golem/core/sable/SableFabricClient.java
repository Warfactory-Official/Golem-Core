package dev.golem.core.sable;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.ArrayList;
import java.util.List;

/** The client half of Fabric's entry to the Sable integration; see {@link SableFabric}. */
public final class SableFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("sable") || !loader.isModLoaded("sodium")) return;
        SableIntegration.clientSetupWithSodium(fluid -> {
            FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid.getType());
            if (handler == null) return List.of();
            List<TextureAtlasSprite> sprites = new ArrayList<>();
            for (TextureAtlasSprite sprite : handler.getFluidSprites(null, null, fluid)) {
                if (sprite != null) sprites.add(sprite);
            }
            return sprites;
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> SableIntegration.renderFrame());
    }
}
