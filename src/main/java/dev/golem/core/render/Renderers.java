package dev.golem.core.render;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Bridges {@link CompatBERProvider} to whichever provider shape the target version's registration API
 * expects.
 *
 * <p>On 26.1 a {@link CompatBER} <i>is</i> a vanilla renderer, so this is just a cast through the
 * two-parameter provider type. On older versions the renderer is wrapped in {@link LegacyBERAdapter},
 * which turns the extract/submit pair back into a single immediate {@code render} call.
 */
public final class Renderers {

    private Renderers() {}

    public static <T extends BlockEntity, S extends CompatRenderState>
            //? if >=26.1 {
            BlockEntityRendererProvider<T, S>
            //?} else {
            /*BlockEntityRendererProvider<T>
            *///?}
            toVanilla(CompatBERProvider<T, S> provider) {
        //? if >=26.1 {
        return provider::create;
        //?} else {
        /*return context -> new LegacyBERAdapter<>(provider.create(context));
        *///?}
    }
}
