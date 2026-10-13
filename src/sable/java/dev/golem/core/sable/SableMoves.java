package dev.golem.core.sable;

import dev.golem.core.GolemCore;
import dev.golem.core.world.SubLevels;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns one call of Sable's {@code SubLevelAssemblyHelper.moveBlocks} into {@link SubLevels#beginMove} /
 * {@link SubLevels#endMove}. The old-to-new map is taken before Sable starts, from the same transform Sable applies.
 */
public final class SableMoves {

    private SableMoves() {}

    /** Moves can nest (a split inside a move); each RETURN closes the innermost. Server thread only. */
    private static final Deque<SubLevels.Move> BATCHES = new ArrayDeque<>();

    public static void begin(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform, Iterable<BlockPos> blocks) {
        Map<BlockPos, BlockPos> moved = new LinkedHashMap<>();
        for (BlockPos pos : blocks) moved.put(pos.immutable(), transform.apply(pos).immutable());
        SubLevels.Move move = new SubLevels.Move(level, transform.getLevel(), moved,
                pos -> transform.apply(pos).immutable(), transform.getRotation());
        BATCHES.push(move);
        SubLevels.beginMove(move.from(), move.to());
    }

    public static void end() {
        SubLevels.Move move = BATCHES.poll();
        if (move == null) return;
        SubLevels.endMove(move);
    }

    /**
     * Closes a batch whose {@code moveBlocks} never returned (it threw). Left open, every later removal in those
     * levels would count as "carried" and drop nothing. Run at the end of every server tick.
     */
    static void closeAbandoned() {
        while (!BATCHES.isEmpty()) {
            GolemCore.LOG.warn("A Sable block move did not complete; closing it ({} block(s))",
                    BATCHES.peek().moved().size());
            end();
        }
    }
}
