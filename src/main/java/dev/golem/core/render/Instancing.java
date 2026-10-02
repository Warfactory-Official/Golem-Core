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

    /**
     * What the engine is holding right now.
     *
     * <p>A correct instanced draw and a correct vanilla draw are pixel-identical, so nothing on screen
     * can tell a working backend from a backend that quietly never received anything. {@code visuals}
     * is the number that settles it — and {@code owning} is the narrower claim that those visuals drew
     * the current frame, which is exactly the question {@link Visuals#owns} asks per block.
     *
     * @param backend the chosen backend's name, or {@code off} / {@code failed} / {@code absent} /
     *                {@code starting}
     * @param visuals declared block entities the engine holds
     * @param healthy of those, the ones that have not failed back to their vanilla renderer
     * @param instances GPU rows written across them
     * @param owning visuals that drew the current frame, so their vanilla renderer stood down
     * @param draws GPU draw calls the last complete frame cost
     * @param buckets instancer buckets those draws covered — equal to {@code draws} without batching
     * @param animated models whose declaration reads state, so their transforms are rewritten each frame
     * @param moving of those, the ones whose transforms actually differed from the previous frame
     */
    public record Report(String backend, int visuals, int healthy, int instances, int owning,
                         int draws, int buckets, int animated, int moving, int writes, int skipped) {}

    /** Never null: a node with no engine reports an absent backend and zeroes. */
    public static Report report() {
        //? if >=26.1 {
        if (!dev.golem.core.flywheel.Flywheel.isAvailable()) {
            return new Report("off", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        if (!dev.golem.core.platform.Platform.isClient()) {
            return new Report("server", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        return Client.report();
        //?} else {
        /*return new Report("absent", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        *///?}
    }

    /** Whether Core provides an instancing engine on this node. */
    public static boolean isAvailable() {
        //? if >=26.1 {
        return dev.golem.core.flywheel.Flywheel.isAvailable();
        //?} else {
        /*return false;
        *///?}
    }

    /** Whether Core's engine is currently drawing the supplied level. */
    public static boolean isVisualizing(@Nullable LevelAccessor level) {
        //? if >=26.1 {
        // Keep the common facade server-safe before resolving the client-only Flywheel implementation.
        // The level-side check is the contract: a dedicated-server query can never be visualized.
        if (level == null || !level.isClientSide()) return false;
        return dev.golem.core.flywheel.Flywheel.isVisualizing(level);
        //?} else {
        /*return false;
        *///?}
    }

    /** Starts Core's backend and installs the consumer's multiblock crumbling resolver. */
    public static void init(@Nullable CrumblingCoreResolver resolver) {
        //? if >=26.1 {
        dev.golem.core.flywheel.Flywheel.init();
        dev.golem.core.flywheel.Flywheel.setCrumblingCoreResolver(
                resolver == null ? null : resolver::coreOf);
        //?}
    }

    //? if >=26.1 {
    /** Client-only report implementation kept out of the common facade's verified method bodies. */
    private static final class Client {

        private Client() {}

        private static Report report() {
            String off = dev.golem.core.flywheel.EngineState.offReason();
            if (off != null) {
                return new Report(dev.golem.core.flywheel.EngineState.failed() ? "failed" : "off",
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            }
            var backend = dev.golem.core.flywheel.backend.BackendManager.selected();
            int[] census = dev.golem.core.flywheel.visual.GemBlockEntityVisual.census();
            return new Report(backend == null ? "starting" : backend.name(),
                    census[0], census[1], census[2], census[3],
                    dev.golem.core.flywheel.DrawStats.draws(), dev.golem.core.flywheel.DrawStats.buckets(),
                    census[4], census[5],
                    dev.golem.core.flywheel.DrawStats.writes(),
                    dev.golem.core.flywheel.DrawStats.skipped());
        }
    }
    //?}
}
