package dev.golem.core.render;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Creates a {@link CompatBER} for a block entity type.
 *
 * <p>Vanilla's own {@code BlockEntityRendererProvider} cannot be used directly by shared code: 26.1
 * gave it a second type parameter for the render state ({@code <T, S>}), while older versions have
 * only {@code <T>}. Arity differences are invisible to a text replacement, so the provider needs a
 * neutral type of its own; {@link Renderers#toVanilla} converts it to whichever shape the target
 * expects.
 *
 * <p>The context type is unchanged across versions, so it is passed through as-is.
 */
@FunctionalInterface
public interface CompatBERProvider<T extends BlockEntity, S extends CompatRenderState> {

    CompatBER<T, S> create(BlockEntityRendererProvider.Context context);
}
