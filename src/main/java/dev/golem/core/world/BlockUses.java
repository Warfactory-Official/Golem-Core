package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A player's block activation after the block has accepted its default use. */
public final class BlockUses {
    private BlockUses() {}

    @FunctionalInterface
    public interface Handler {
        void used(Level level, BlockPos pos, BlockState state, Player player);
    }

    public static final Hook<Handler> AFTER = Hook.notifying();

    public static void accepted(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide()) return;
        for (var handler : AFTER) handler.used(level, pos, state, player);
    }
}
