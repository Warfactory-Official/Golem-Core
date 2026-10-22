package dev.golem.core.render;

import dev.golem.core.gemrender.GemRender;
import dev.golem.core.platform.Platform;
import net.minecraft.world.level.LevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * GPU instancing through GemRender, an optional client mod carrying Flywheel; without it, or with the backend off, machines
 * draw through their vanilla renderers. {@link GemRender} is touched only after {@link #isAvailable}.
 */
public final class Instancing {

    public static final String MOD_ID = "gemrender";

    private static @Nullable Boolean available;

    private Instancing() {}

    /**
     * What the engine holds: an instanced and a vanilla draw look the same, so counts tell a working backend from
     * one that received nothing. {@code draws} is -1 (Flywheel does not count them); {@code buckets} counts
     * (mesh, surface) models; {@code moving} is the animated models whose transforms changed this frame.
     */
    public record Report(String backend, int visuals, int healthy, int instances, int owning,
                         int draws, int buckets, int animated, int moving, int writes, int skipped) {}

    /** Whether GemRender is installed on this client. */
    public static boolean isAvailable() {
        Boolean known = available;
        if (known == null) {
            known = Platform.isClient() && Platform.isModLoaded(MOD_ID);
            available = known;
        }
        return known;
    }

    /** Whether GemRender is drawing {@code level}: installed, and its backend is not switched off. */
    public static boolean isVisualizing(@Nullable LevelAccessor level) {
        if (level == null || !level.isClientSide() || !isAvailable()) return false;
        return GemRender.isVisualizing(level);
    }

    /** Never null: a client without GemRender reports an absent backend and zeroes. */
    public static Report report() {
        if (!Platform.isClient()) return new Report("server", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        if (!isAvailable()) return new Report("absent", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        return GemRender.report();
    }
}
