package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every block state change in a server level by any cause, after the chunk holds the new state; not world generation or
 * chunk loading. Handlers run for every change, so they must be cheap.
 */
public final class BlockChanges {

    private BlockChanges() {}

    @FunctionalInterface
    public interface Handler {
        void onChange(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState);
    }

    public static final Hook<Handler> HANDLERS = Hook.notifying();

    public static void onChange(Handler handler) {
        HANDLERS.register(handler);
    }

    public static void changed(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (HANDLERS.isEmpty() || oldState == newState) return;
        for (Handler handler : HANDLERS) handler.onChange(level, pos, oldState, newState);
    }
}
