package dev.golem.core.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

//? if >=26.1 {
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
//?}

/**
 * A block in flight (a falling block, a thrown chunk of a structure), drawn with the world lighting
 * of the position it is passing through, the way vanilla's falling-block renderer draws sand.
 *
 * <p>26.1 extracts a {@code MovingBlockRenderState} carrying the biome, light engine and cardinal
 * lighting the block model needs, and the collector tesselates it later. Older versions tesselate
 * immediately through {@code BlockRenderDispatcher#renderBatched} against the live level. Both are
 * fed from {@link #extract} and drawn through {@link SubmitSink#submitMovingBlock}.
 */
public final class CompatMovingBlock {

    //? if >=26.1 {
    final MovingBlockRenderState delegate = new MovingBlockRenderState();
    //?} else {
    /*@Nullable Level level;
    @Nullable BlockPos pos;
    @Nullable BlockPos seedPos;
    *///?}

    private @Nullable BlockState state;

    /** True when nothing would be drawn: no block, or one without a model shape. */
    public boolean isEmpty() {
        return state == null || state.getRenderShape() != RenderShape.MODEL;
    }

    public @Nullable BlockState blockState() {
        return state;
    }

    /**
     * Prepares {@code block} as it would look at {@code pos}.
     *
     * @param seedPos the position whose hash picks the model variant, so a block keeps the face it
     *                had before it started moving
     */
    public void extract(Level level, BlockState block, BlockPos pos, BlockPos seedPos) {
        this.state = block;
        //? if >=26.1 {
        delegate.blockState = block;
        delegate.blockPos = pos;
        delegate.randomSeedPos = seedPos;
        if (level instanceof ClientLevel clientLevel) {
            delegate.biome = clientLevel.getBiome(pos);
            delegate.cardinalLighting = clientLevel.cardinalLighting();
            delegate.lightEngine = clientLevel.getLightEngine();
        }
        //?} else {
        /*this.level = level;
        this.pos = pos;
        this.seedPos = seedPos;
        *///?}
    }
}
