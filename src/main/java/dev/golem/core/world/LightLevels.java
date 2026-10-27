package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;
//? if >=1.21 {
import net.minecraft.world.level.chunk.status.ChunkStatus;
//?} else {
/*import net.minecraft.world.level.chunk.ChunkStatus;
*///?}

/**
 * Legacy light queries: {@code World.getBlockLightValue} and {@code World.getBlockLightOpacity}.
 *
 * <p>A block's opacity is a spelling change: {@code getLightBlock(BlockGetter, BlockPos)} through 1.21.1,
 * the cached {@code getLightDampening()} from 1.21.2 on. The value means the same thing (0 for air and
 * glass, 15 for a full opaque block).
 */
public final class LightLevels {

    private LightLevels() {}

    /** {@code getBlockLightOpacity}: how much light the block at {@code pos} absorbs, 0..15. */
    public static int opacity(BlockGetter level, BlockPos pos) {
        //? if >=1.21.2 {
        return level.getBlockState(pos).getLightDampening();
        //?} else {
        /*return level.getBlockState(pos).getLightBlock(level, pos);
        *///?}
    }

    /** How much light {@code state} absorbs, 0..15, from vanilla's per-state cache (position-independent). */
    public static int opacity(net.minecraft.world.level.block.state.BlockState state) {
        //? if >=1.21.2 {
        return state.getLightDampening();
        //?} else {
        /*return state.getLightBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        *///?}
    }

    /** The first position above a live light-blocking column, as the 1.7 chunk heightmap measured it. */
    public static int lightBlockingHeight(LevelReader level, int x, int z) {
        var chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.EMPTY, false);
        var highest = chunk.getHighestFilledSectionIndex();
        //? if >=26.1 {
        var min = chunk.getMinY();
        var max = chunk.getMaxY();
        //?} else {
        /*var min = chunk.getMinBuildHeight();
        var max = chunk.getMaxBuildHeight();
        *///?}
        var top = highest < 0 ? min : Math.min((chunk.getSectionYFromSectionIndex(highest) << 4) + 15, max);
        var pos = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y >= min; y--) {
            if (opacity(chunk, pos.setY(y)) > 0) return y + 1;
        }
        return min;
    }

    /** Whether sky light passes down through {@code state} at full strength (vanilla's per-state cache). */
    public static boolean propagatesSkylightDown(net.minecraft.world.level.block.state.BlockState state) {
        //? if >=1.21.2 {
        return state.propagatesSkylightDown();
        //?} else {
        /*return state.propagatesSkylightDown(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        *///?}
    }

    /** Whether sky light stops at {@code lower} ({@code pos}) under {@code upper}: vanilla's ChunkSkyLightSources rule. */
    public static boolean stopsSkylight(BlockGetter level, BlockPos pos, BlockState upper, BlockState lower) {
        //? if >=1.21.2 {
        return opacity(lower) != 0 || Shapes.faceShapeOccludes(
                LightEngine.getOcclusionShape(upper, Direction.DOWN),
                LightEngine.getOcclusionShape(lower, Direction.UP));
        //?} else {
        /*return opacity(level, pos) != 0 || Shapes.faceShapeOccludes(
                LightEngine.getOcclusionShape(level, pos.above(), upper, Direction.DOWN),
                LightEngine.getOcclusionShape(level, pos, lower, Direction.UP));
        *///?}
    }

    /** {@code getBlockLightValue}: the brighter of block light and sky light (darkened by time of day). */
    public static int combined(LevelReader level, BlockPos pos) {
        return level.getMaxLocalRawBrightness(pos);
    }
}
