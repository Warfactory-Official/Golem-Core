package dev.golem.core.graph;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Block entity announcing its own arrival ({@code clearRemoved}, every chunk load; idempotent) =>
 * {@link LevelNodeGraph#onChunkFull} skips it.
 */
public interface GraphResident {

    static void onLoad(BlockEntity be) {
        if (be.getLevel() instanceof ServerLevel server) LevelNodeGraph.invalidateEndpointsAround(server, be.getBlockPos());
    }
}
