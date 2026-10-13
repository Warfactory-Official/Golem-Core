package dev.golem.core.flywheel.visual;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a mined block position to the position that actually owns the visual.
 *
 * <p>A multiblock is usually mined by hitting one of its dummy blocks, and a dummy carries no block
 * entity, so the direct visual lookup misses and the crumbling overlay does not draw on
 * any multiblock.
 *
 * <p>CE resolves this inline with {@code BlockDummyable.findCore}. That type is content and this layer
 * cannot name it, so the content layer installs a hook through
 * {@link dev.golem.core.flywheel.Flywheel#setCrumblingCoreResolver}. Without it only multiblocks lose
 * the overlay; single blocks resolve directly.
 *
 * <p>Declared outside the engine's compile guard so that a consumer can install a resolver without
 * knowing whether this node has an engine to use it.
 */
@FunctionalInterface
public interface CrumblingCoreResolver {

    /** The visual-owning position for {@code mined}, or {@code null} if there is none. */
    @Nullable BlockPos coreOf(Level level, BlockPos mined, BlockState state);
}
