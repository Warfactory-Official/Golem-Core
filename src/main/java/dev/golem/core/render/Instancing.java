package dev.golem.core.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Optional GPU-instancing backend exposed without leaking an engine implementation to consumers. */
public final class Instancing {

    @FunctionalInterface
    public interface CrumblingCoreResolver {
        @Nullable BlockPos coreOf(Level level, BlockPos mined, BlockState state);
    }

    private Instancing() {}

    /** Whether Core provides an instancing engine on this node. */
    public static boolean isAvailable() {
        //? if neoforge && >=26.1 {
        return dev.golem.core.flywheel.Flywheel.isAvailable();
        //?} else {
        /*return false;
        *///?}
    }

    /** Whether Core's engine is currently drawing the supplied level. */
    public static boolean isVisualizing(@Nullable LevelAccessor level) {
        //? if neoforge && >=26.1 {
        return dev.golem.core.flywheel.Flywheel.isVisualizing(level);
        //?} else {
        /*return false;
        *///?}
    }

    /** Starts Core's backend and installs the consumer's multiblock crumbling resolver. */
    public static void init(@Nullable CrumblingCoreResolver resolver) {
        //? if neoforge && >=26.1 {
        dev.golem.core.flywheel.Flywheel.init();
        dev.golem.core.flywheel.Flywheel.setCrumblingCoreResolver(
                resolver == null ? null : resolver::coreOf);
        //?}
    }
}
