package dev.golem.core.fluid;

import dev.golem.core.Ids;

/**
 * The client half of {@link WorldFluids}: the still and flowing sprites, tint and chunk layer each loader
 * draws a world fluid with. 1.7's render pass 1 is the translucent layer, pass 0 the solid one.
 *
 * <ul>
 *   <li>26.1 (both loaders): a {@code FluidModel}, whose layer follows its materials' transparency, so a
 *       translucent fluid's materials are forced translucent.</li>
 *   <li>NeoForge 1.21.1 and Forge 1.20.1: the fluid type's client extension, and the fluid's render layer.</li>
 *   <li>Fabric below 26.1: Fabric API's fluid render handler and block render layer map.</li>
 * </ul>
 */
final class WorldFluidsClient {

    private WorldFluidsClient() {}

    private static final int UNTINTED = 0xFFFFFFFF;

    static void install() {
        //? if neoforge && >=26.1 {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterFluidModelsEvent event) -> {
                    for (WorldFluids.Handle handle : WorldFluids.declared()) {
                        WorldFluids.Spec spec = handle.spec();
                        net.neoforged.neoforge.client.fluid.FluidTintSource tint = spec.tint() == UNTINTED ? null : state -> spec.tint();
                        event.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                                material(spec.stillSprite(), spec.translucent()), material(spec.flowingSprite(), spec.translucent()),
                                null, tint), handle.source(), handle.flowing());
                    }
                });
        //?} elif neoforge {
        /*dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) -> {
                    for (WorldFluids.Handle handle : WorldFluids.declared()) {
                        event.registerFluidType(extensions(handle::spec), handle.type.get());
                    }
                });
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(WorldFluidsClient::layers));
        *///?} elif forge {
        /*dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(WorldFluidsClient::layers));
        *///?} elif fabric && >=26.1 {
        /*for (WorldFluids.Handle handle : WorldFluids.declared()) {
            WorldFluids.Spec spec = handle.spec();
            net.minecraft.client.color.block.BlockTintSource tint = spec.tint() == UNTINTED ? null : state -> spec.tint();
            net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry.register(handle.source(), handle.flowing(),
                    new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                            material(spec.stillSprite(), spec.translucent()), material(spec.flowingSprite(), spec.translucent()),
                            null, tint));
        }
        *///?} else {
        /*for (WorldFluids.Handle handle : WorldFluids.declared()) {
            WorldFluids.Spec spec = handle.spec();
            net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry.INSTANCE.register(
                    handle.source(), handle.flowing(),
                    new net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler(
                            Ids.parse(spec.stillSprite()), Ids.parse(spec.flowingSprite()),
                            spec.tint() == UNTINTED ? -1 : spec.tint()));
            if (spec.translucent()) {
                net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putFluids(
                        net.minecraft.client.renderer.RenderType.translucent(), handle.source(), handle.flowing());
            }
        }
        *///?}
    }

    //? if >=26.1 {
    private static net.minecraft.client.resources.model.sprite.Material material(String sprite, boolean translucent) {
        return new net.minecraft.client.resources.model.sprite.Material(Ids.parse(sprite), translucent);
    }
    //?}

    //? if forgelike && <26.1 {
    /*private static void layers() {
        for (WorldFluids.Handle handle : WorldFluids.declared()) {
            if (!handle.spec().translucent()) continue;
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(handle.source(), net.minecraft.client.renderer.RenderType.translucent());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(handle.flowing(), net.minecraft.client.renderer.RenderType.translucent());
        }
    }
    *///?}

    //? if neoforge && <26.1 {
    /*static net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions extensions(java.util.function.Supplier<WorldFluids.Spec> spec) {
        return new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            @Override public net.minecraft.resources.ResourceLocation getStillTexture() { return Ids.parse(spec.get().stillSprite()); }
            @Override public net.minecraft.resources.ResourceLocation getFlowingTexture() { return Ids.parse(spec.get().flowingSprite()); }
            @Override public int getTintColor() { return spec.get().tint(); }
        };
    }
    *///?} elif forge {
    /*static net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions extensions(java.util.function.Supplier<WorldFluids.Spec> spec) {
        return new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions() {
            @Override public net.minecraft.resources.ResourceLocation getStillTexture() { return Ids.parse(spec.get().stillSprite()); }
            @Override public net.minecraft.resources.ResourceLocation getFlowingTexture() { return Ids.parse(spec.get().flowingSprite()); }
            @Override public int getTintColor() { return spec.get().tint(); }
        };
    }
    *///?}
}
