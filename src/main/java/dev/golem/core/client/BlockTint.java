package dev.golem.core.client;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Set;

/**
 * A block's tint colour, derived from its state alone.
 *
 * <p>26.1 replaces the per-quad {@code BlockColor} callback (level, position, tint index) with
 * {@code BlockTintSource}, a state-keyed provider that also declares which properties it reads so the tint can be
 * cached per state. Older versions have only the callback.
 *
 * <p>This is the 26.1 shape because it is the narrower one. On older versions {@link Tints#registerBlockTint} adapts it
 * into a {@code BlockColor} that ignores level and position.
 */
public interface BlockTint {

    /** The ARGB tint for this state. */
    int color(BlockState state);

    /**
     * The state properties {@link #color} reads. 26.1 uses these to key its tint cache; older versions
     * recompute per quad and ignore them, so an incomplete set is a 26.1-only correctness bug.
     */
    Set<Property<?>> relevantProperties();
}
