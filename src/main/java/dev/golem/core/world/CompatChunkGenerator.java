package dev.golem.core.world;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.StructureManager;

/**
 * A chunk generator written once against a neutral shape: the version-specific abstract methods of vanilla's
 * {@link ChunkGenerator} (an {@code Executor} on 1.20.1's {@code fillFromNoise}, a carving step on its
 * {@code applyCarvers}, a {@code Codec} instead of a {@code MapCodec} below 1.20.5) are implemented here and forwarded
 * to {@link #fill}, {@link #carve} and {@link #surface}.
 *
 * <p>{@link #seed()} is the level seed, which vanilla's {@code RandomState} does not expose: it is captured from
 * {@code createState}, which every level calls once before generating anything, and passed on to the generator's biome
 * source when that is a {@link CompatBiomeSource}.
 *
 * <p>The codec is registered through {@link Dimensions#chunkGenerator}; the dimension, its type and its biome are
 * authored JSON.
 */
public abstract class CompatChunkGenerator extends ChunkGenerator {

    private volatile long seed;
    private volatile boolean seeded;

    protected CompatChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    /** This generator's codec, as registered. */
    protected abstract MapCodec<? extends ChunkGenerator> mapCodec();

    //? if >=1.20.5 {
    @Override
    protected final MapCodec<? extends ChunkGenerator> codec() {
        return mapCodec();
    }
    //?} else {
    /*@Override
    protected final com.mojang.serialization.Codec<? extends ChunkGenerator> codec() {
        return Dimensions.legacyCodec(mapCodec());
    }
    *///?}

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState,
                                                    long levelSeed) {
        this.seed = levelSeed;
        this.seeded = true;
        if (getBiomeSource() instanceof CompatBiomeSource source) source.useSeed(levelSeed);
        return super.createState(structureSets, randomState, levelSeed);
    }

    /**
     * Gives a generator built outside a level (a GameTest, a preview) its seed; a level's own generator gets it from
     * {@code createState}. Returns this generator.
     */
    public CompatChunkGenerator useSeed(long levelSeed) {
        this.seed = levelSeed;
        this.seeded = true;
        if (getBiomeSource() instanceof CompatBiomeSource source) source.useSeed(levelSeed);
        return this;
    }

    /** The level seed; available once the level has created its generator state (before any chunk). */
    protected final long seed() {
        if (!seeded) throw new IllegalStateException(getClass().getName() + " asked for its seed before the level set it");
        return seed;
    }

    /** The NOISE step: write the chunk's terrain (use {@link #set} to keep the generation heightmaps right). */
    protected abstract void fill(ChunkAccess chunk);

    /**
     * The lowest y this generator fills (the dimension type's {@code min_y}). Vanilla's {@code getMinY} keeps that name on
     * every version while a level's accessor of the same name was {@code getMinBuildHeight} before 26.1, so a mod's
     * source rename for the accessor would also rename an override of it; this neutral name avoids that.
     */
    protected abstract int minimumY();

    @Override
    public final int getMinY() {
        return minimumY();
    }

    /** The CARVERS step; nothing by default. */
    protected void carve(WorldGenRegion region, ChunkAccess chunk) {}

    /** The SURFACE step; nothing by default. */
    protected void surface(WorldGenRegion region, ChunkAccess chunk) {}

    /**
     * Sets a block in a chunk being generated and updates its two generation heightmaps, as vanilla's fills do.
     *
     * <p>A block with a block entity also gets vanilla's {@code DUMMY} pending block-entity tag: a chunk filled at the
     * noise step creates no block entities, and without the tag the block would stay without one (its entity never
     * ticks) until something asked for it. The full chunk creates it when it promotes its pending entities.
     */
    protected static void set(ChunkAccess chunk, BlockPos.MutableBlockPos pos, BlockState state) {
        //? if >=26.1 {
        chunk.setBlockState(pos, state);
        //?} else {
        /*chunk.setBlockState(pos, state, false);
        *///?}
        if (state.hasBlockEntity()) {
            net.minecraft.nbt.CompoundTag pending = new net.minecraft.nbt.CompoundTag();
            pending.putString("id", "DUMMY");
            pending.putInt("x", pos.getX());
            pending.putInt("y", pos.getY());
            pending.putInt("z", pos.getZ());
            chunk.setBlockEntityNbt(pending);
        }
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG).update(pos.getX() & 15, pos.getY(), pos.getZ() & 15, state);
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).update(pos.getX() & 15, pos.getY(), pos.getZ() & 15, state);
    }

    //? if >=1.20.5 {
    @Override
    public final CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                              StructureManager structureManager, ChunkAccess chunk) {
        fill(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    //?} else {
    /*@Override
    public final CompletableFuture<ChunkAccess> fillFromNoise(java.util.concurrent.Executor executor, Blender blender,
            RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        fill(chunk);
        return CompletableFuture.completedFuture(chunk);
    }
    *///?}

    // 1.21.2 dropped the carving step (only air carvers remain); before it the method ran once per step.
    //? if >=1.21.2 {
    @Override
    public final void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
                                   StructureManager structureManager, ChunkAccess chunk) {
        carve(region, chunk);
    }
    //?} else {
    /*@Override
    public final void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
            StructureManager structureManager, ChunkAccess chunk,
            net.minecraft.world.level.levelgen.GenerationStep.Carving step) {
        if (step == net.minecraft.world.level.levelgen.GenerationStep.Carving.AIR) carve(region, chunk);
    }
    *///?}

    @Override
    public final void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState,
                                   ChunkAccess chunk) {
        surface(region, chunk);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {}
}
