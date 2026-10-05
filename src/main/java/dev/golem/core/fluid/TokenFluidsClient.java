package dev.golem.core.fluid;

import dev.golem.core.Ids;

/**
 * The client half of {@link TokenFluids}: how each loader learns which sprite and tint draw a token fluid. A
 * token fluid is never placed, so this is only ever seen where another mod draws a fluid by itself: a tank
 * gauge, a recipe viewer's fluid slot.
 */
final class TokenFluidsClient {

    private TokenFluidsClient() {}

    private static final int UNTINTED = 0xFFFFFFFF;

    static void install() {
        //? if neoforge && >=26.1 {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterFluidModelsEvent event) -> {
                    for (TokenFluids.Registered registered : TokenFluids.registered()) {
                        TokenFluids.Spec spec = registered.spec();
                        net.minecraft.client.resources.model.sprite.Material material =
                                new net.minecraft.client.resources.model.sprite.Material(Ids.parse(spec.sprite()));
                        net.neoforged.neoforge.client.fluid.FluidTintSource tint =
                                spec.tint() == UNTINTED ? null : state -> spec.tint();
                        event.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                                material, material, null, tint), registered.fluid().get());
                    }
                });
        //?} elif neoforge {
        /*dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) -> {
                    for (TokenFluids.Registered registered : TokenFluids.registered()) {
                        event.registerFluidType(extensions(registered::spec), registered.fluid().get().getFluidType());
                    }
                });
        *///?} elif forge {
        /*return;
        *///?} elif fabric && >=26.1 {
        /*for (TokenFluids.Registered registered : TokenFluids.registered()) {
            TokenFluids.Spec spec = registered.spec();
            net.minecraft.client.resources.model.sprite.Material material =
                    new net.minecraft.client.resources.model.sprite.Material(Ids.parse(spec.sprite()));
            net.minecraft.client.color.block.BlockTintSource tint = spec.tint() == UNTINTED ? null : state -> spec.tint();
            net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry.register(registered.fluid().get(),
                    new net.minecraft.client.renderer.block.FluidModel.Unbaked(material, material, null, tint));
        }
        *///?} else {
        /*for (TokenFluids.Registered registered : TokenFluids.registered()) {
            TokenFluids.Spec spec = registered.spec();
            net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry.INSTANCE.register(
                    registered.fluid().get(),
                    new net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler(
                            Ids.parse(spec.sprite()), Ids.parse(spec.sprite()), spec.tint() == UNTINTED ? -1 : spec.tint()));
        }
        *///?}
    }

    //? if neoforge && <26.1 {
    /*static net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions extensions(java.util.function.Supplier<TokenFluids.Spec> spec) {
        return new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            @Override public net.minecraft.resources.ResourceLocation getStillTexture() { return Ids.parse(spec.get().sprite()); }
            @Override public net.minecraft.resources.ResourceLocation getFlowingTexture() { return Ids.parse(spec.get().sprite()); }
            @Override public int getTintColor() { return spec.get().tint(); }
        };
    }
    *///?} elif forge {
    /*static net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions extensions(java.util.function.Supplier<TokenFluids.Spec> spec) {
        return new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions() {
            @Override public net.minecraft.resources.ResourceLocation getStillTexture() { return Ids.parse(spec.get().sprite()); }
            @Override public net.minecraft.resources.ResourceLocation getFlowingTexture() { return Ids.parse(spec.get().sprite()); }
            @Override public int getTintColor() { return spec.get().tint(); }
        };
    }
    *///?}
}
