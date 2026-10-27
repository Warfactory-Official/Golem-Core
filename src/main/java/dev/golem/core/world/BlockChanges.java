package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every block state change in a server level by any cause, after the chunk holds the new state; not world generation or
 * chunk loading. Handlers run for every change, so they must be cheap.
 *
 * <p>Raw section writers publish through {@link #committed} after completing their batch. Unlike entity placement and
 * player break events, both paths also cover machines, explosions, and property changes such as an opening door.
 */
public final class BlockChanges {

    private BlockChanges() {}

    @FunctionalInterface
    public interface Handler {
        /** {@code pos} may be mutable; retain its packed value or an immutable copy. */
        void onChange(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState);

        /** Lets bulk writers skip recording changes in chunks no listener observes. Must not load chunks. */
        default boolean observesChunk(ServerLevel level, int chunkX, int chunkZ) {
            return true;
        }
    }

    public static final Hook<Handler> HANDLERS = Hook.notifying();

    public static void onChange(Handler handler) {
        HANDLERS.register(handler);
    }

    /** Whether any listener observes this chunk's edits (bulk writers skip recording otherwise). */
    public static boolean observesChunk(ServerLevel level, int chunkX, int chunkZ) {
        var listeners = HANDLERS.listeners();
        for (int i = 0; i < listeners.size(); i++) {
            if (listeners.get(i).observesChunk(level, chunkX, chunkZ)) return true;
        }
        return false;
    }

    public static void changed(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (HANDLERS.isEmpty() || oldState == newState) return;
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        var listeners = HANDLERS.listeners();
        for (int i = 0; i < listeners.size(); i++) {
            var handler = listeners.get(i);
            if (handler.observesChunk(level, chunkX, chunkZ)) handler.onChange(level, pos, oldState, newState);
        }
    }

    /** Publishes a completed change, without copying the caller's position or loading a chunk. */
    public static void committed(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        if (before == after || HANDLERS.isEmpty()) return;
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        var listeners = HANDLERS.listeners();
        for (int i = 0; i < listeners.size(); i++) {
            var handler = listeners.get(i);
            if (handler.observesChunk(level, chunkX, chunkZ)) handler.onChange(level, pos, before, after);
        }
    }
}
