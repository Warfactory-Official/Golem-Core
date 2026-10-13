package dev.golem.core.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

//? if >=26.1 {
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.CardinalLighting;
//?} else {
/*import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
*///?}

/**
 * A scoop of the world held outside it: a cube of block states with its own lighting and biome
 * tint, which a block renderer can tesselate as if it were a level.
 *
 * <p>A large blast throws these: rubble that is the ground it came from, mesh and all, rather than a
 * textured billboard. Upstream calls it a world in a jar and also uses it for scripted scenes; this is
 * the render half, which is what a client effect needs.
 *
 * <p>It is a level view and not a baked mesh because the block renderer then culls faces between two
 * filled cells, tints grass by the biome it was cut from, and draws every model quirk (connected
 * fences, stair shapes, cross-shaped plants) with the same code as the terrain.
 *
 * <p>Light and tint are frozen when the scoop is cut. A chunk of ground in flight is lit by where it
 * came from, as in 1.7, and the jar stays independent of the level once it exists.
 */
public final class WorldInAJar implements BlockAndTintGetter {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;

    private final BlockState[] cells;

    private int filled;
    private int blockLight;
    private int skyLight = 15;
    private int grassTint = 0xFFFFFF;
    private int foliageTint = 0xFFFFFF;
    private int waterTint = 0xFFFFFF;

    public WorldInAJar(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = Math.max(0, sizeX);
        this.sizeY = Math.max(0, sizeY);
        this.sizeZ = Math.max(0, sizeZ);
        this.cells = new BlockState[this.sizeX * this.sizeY * this.sizeZ];
    }

    /**
     * Cuts a jagged scoop out of {@code level} centred on {@code (cX, cY, cZ)}.
     *
     * <p>The shape is upstream's and it is not a cube: the middle two-by-two-by-two is
     * always taken, then each shell outwards gets {@code retry} random darts, and a dart only lands if
     * the cell it hits already touches something solid in the jar. The rubble grows outwards from a
     * core and comes out ragged, and a scoop taken over a cliff edge or open water keeps the hole.
     *
     * @param size  edge of the jar in blocks; the scoop reaches {@code size / 2} out from the middle
     * @param retry darts thrown per shell: how solid the result is, not how big
     */
    public static WorldInAJar fromLevel(ClientLevel level, int cX, int cY, int cZ, int size, int retry,
            RandomSource random) {
        WorldInAJar jar = new WorldInAJar(size, size, size);
        if (size <= 0) return jar;

        int middle = size / 2 - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int k = 0; k < 2; k++) {
                    jar.setBlock(middle + i, middle + j, middle + k,
                            level.getBlockState(cursor.set(cX + i, cY + j, cZ + k)));
                }
            }
        }

        for (int layer = 2; layer <= size / 2; layer++) {
            for (int dart = 0; dart < retry; dart++) {
                int jx = -layer + random.nextInt(layer * 2 + 1);
                int jy = -layer + random.nextInt(layer * 2 + 1);
                int jz = -layer + random.nextInt(layer * 2 + 1);
                if (!jar.touchesSolid(middle + jx, middle + jy, middle + jz)) continue;
                jar.setBlock(middle + jx, middle + jy, middle + jz,
                        level.getBlockState(cursor.set(cX + jx, cY + jy, cZ + jz)));
            }
        }

        jar.sampleFrom(level, cursor.set(cX, cY, cZ));
        return jar;
    }

    /**
     * Sets the jar's grass, foliage and water colours outright, for a jar that was not cut from a level:
     * a scripted scene set in a fixed biome.
     */
    public void tint(int grass, int foliage, int water) {
        // Opaque whatever the caller passed: 26.1 multiplies faces by the ARGB tint, and an RGB colour's
        // alpha of 0 makes every tinted face vanish under the cutout test. Older targets read only RGB.
        this.grassTint = 0xFF000000 | grass;
        this.foliageTint = 0xFF000000 | foliage;
        this.waterTint = 0xFF000000 | water;
    }

    /** Freezes the light and biome tint of {@code pos} into the jar. */
    public void sampleFrom(ClientLevel level, BlockPos pos) {
        this.blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        this.skyLight = level.getBrightness(LightLayer.SKY, pos);
        this.grassTint = level.getBlockTint(pos, BiomeColors.GRASS_COLOR_RESOLVER);
        this.foliageTint = level.getBlockTint(pos, BiomeColors.FOLIAGE_COLOR_RESOLVER);
        this.waterTint = level.getBlockTint(pos, BiomeColors.WATER_COLOR_RESOLVER);
    }

    public void setBlock(int x, int y, int z, BlockState state) {
        if (outside(x, y, z)) return;
        int index = index(x, y, z);
        boolean wasFilled = cells[index] != null && !cells[index].isAir();
        boolean nowFilled = state != null && !state.isAir();
        cells[index] = state;
        if (wasFilled != nowFilled) filled += nowFilled ? 1 : -1;
    }

    public BlockState getBlockState(int x, int y, int z) {
        if (outside(x, y, z)) return AIR;
        BlockState state = cells[index(x, y, z)];
        return state != null ? state : AIR;
    }

    /** Whether the scoop came up empty, which happens over water and off a cliff edge. */
    public boolean isEmpty() {
        return filled == 0;
    }

    /** How many cells hold something, for sizing the work a draw is about to do. */
    public int filledCells() {
        return filled;
    }

    public int blockLight() {
        return blockLight;
    }

    public int skyLight() {
        return skyLight;
    }

    private boolean touchesSolid(int x, int y, int z) {
        return !getBlockState(x + 1, y, z).isAir() || !getBlockState(x - 1, y, z).isAir()
                || !getBlockState(x, y + 1, z).isAir() || !getBlockState(x, y - 1, z).isAir()
                || !getBlockState(x, y, z + 1).isAir() || !getBlockState(x, y, z - 1).isAir();
    }

    private boolean outside(int x, int y, int z) {
        return x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ;
    }

    private int index(int x, int y, int z) {
        return (x * sizeY + y) * sizeZ + z;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return getBlockState(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return sizeY;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return layer == LightLayer.SKY ? skyLight : blockLight;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return Math.max(blockLight, skyLight - darkening);
    }

    /**
     * The jar's light is frozen, so nothing should ever reach the engine: {@link #getBrightness} and
     * {@link #getRawBrightness} answer above it. Only 26.1 has an empty engine to hand back.
     */
    @Override
    public LevelLightEngine getLightEngine() {
        //? if >=26.1 {
        return LevelLightEngine.EMPTY;
        //?} else {
        /*return null;
        *///?}
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        if (resolver == BiomeColors.WATER_COLOR_RESOLVER) return waterTint;
        if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER) return foliageTint;
        return grassTint;
    }

    //? if >=26.1 {
    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return CardinalLighting.DEFAULT;
    }
    //?} else {
    /*@Override
    public int getMinBuildHeight() {
        return 0;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        if (!shade) return 1F;
        return switch (direction) {
            case DOWN -> 0.5F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
            default -> 1F;
        };
    }
    *///?}
}
