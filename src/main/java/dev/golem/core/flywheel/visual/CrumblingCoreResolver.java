package dev.golem.core.flywheel.visual;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a mined block position to the position that actually owns the visual.
 *
 * <p>A multiblock is usually mined by hitting one of its dummy blocks, and a dummy carries no block
 * entity — so the direct visual lookup misses and the crumbling overlay silently fails to draw on
 * every multiblock in the game.
 *
 * <p>CE resolves this inline, testing the mined block against {@code BlockDummyable} and calling its
 * {@code findCore}. That type is content, and this layer cannot name it, so the resolution is
 * inverted into a hook the content layer installs through
 * {@link dev.golem.core.flywheel.Flywheel#setCrumblingCoreResolver}. Leaving it uninstalled costs
 * only the overlay on multiblocks; single blocks resolve directly and are unaffected.
 *
 * <p>Declared outside the engine's compile guard so that a consumer can install a resolver without
 * knowing whether this node has an engine to use it.
 */
@FunctionalInterface
public interface CrumblingCoreResolver {

    /** The visual-owning position for {@code mined}, or {@code null} if there is none. */
    @Nullable BlockPos coreOf(Level level, BlockPos mined, BlockState state);
}
