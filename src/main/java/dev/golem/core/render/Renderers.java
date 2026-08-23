package dev.golem.core.render;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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

    /**
     * Samples the packed combined (sky + block) light at a world position.
     *
     * <p>26.1 renamed the method {@code getLightColor} → {@code getLightCoords} and its receiver
     * {@code BlockAndTintGetter} → {@code BlockAndLightGetter}; renderers that draw geometry away from
     * their block entity's own position (sliding rods, stacked column segments) need this to light each
     * piece by the block it actually occupies, as baked models always did.
     */
    public static int lightAt(Level level, BlockPos pos) {
        //? if >=26.1 {
        return net.minecraft.client.renderer.LevelRenderer.getLightCoords((net.minecraft.world.level.BlockAndLightGetter) level, pos);
        //?} else {
        /*return net.minecraft.client.renderer.LevelRenderer.getLightColor((net.minecraft.world.level.BlockAndTintGetter) level, pos);
        *///?}
    }
}
