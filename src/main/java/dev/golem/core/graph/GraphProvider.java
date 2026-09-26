package dev.golem.core.graph;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.block.state.BlockState;

public interface GraphProvider<D> {

    D createData(BlockState state);

    boolean dataCompatible(D a, D b);

    Codec<D> dataCodec();

    /** Per-member rate caps (conductors uncapped, diodes limit). */
    default boolean hasTieredRates() {
        return false;
    }

    /**
     * Two nets are about to merge; drain's object is discarded once its members move, so a distributor keying per-net state on the NET OBJECT folds the dying side's state in here. Splits need no counterpart - the largest component keeps its net object. Server thread.
     */
    default void onNetsMerged(NodeNetwork<D> keep, NodeNetwork<D> drain) {
    }

    default long rateOf(D data) {
        return Long.MAX_VALUE;
    }
}
