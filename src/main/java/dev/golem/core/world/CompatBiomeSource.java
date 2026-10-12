package dev.golem.core.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.biome.BiomeSource;

/**
 * A biome source written once against a neutral shape: vanilla's {@link BiomeSource#codec()} returns a {@code MapCodec}
 * from 1.20.5 and a plain {@code Codec} below; a mod implements {@link #mapCodec()} and registers that codec with
 * {@link Dimensions#biomeSource}. {@code collectPossibleBiomes} and {@code getNoiseBiome} have the same shape on every
 * supported version and are implemented directly.
 *
 * <p>{@link #seed()} is the level seed, which vanilla hands a biome source only inside a {@code Climate.Sampler}: a
 * {@link CompatChunkGenerator} passes its seed to its biome source when the level creates the generator state (before any
 * chunk or biome query), and in {@link CompatChunkGenerator#useSeed}. A {@code CompatBiomeSource} that needs the seed
 * therefore belongs under a {@code CompatChunkGenerator}; under any other generator it is never seeded and
 * {@link #seed()} throws.
 *
 * <p>Biome queries arrive from vanilla's worker threads ({@code init_biomes}, {@code /locate biome}, spawn search), so an
 * implementation must be thread-safe.
 */
public abstract class CompatBiomeSource extends BiomeSource {

    private volatile long seed;
    private volatile boolean seeded;

    protected CompatBiomeSource() {
        super();
    }

    /** This biome source's codec, as registered. */
    protected abstract MapCodec<? extends BiomeSource> mapCodec();

    //? if >=1.20.5 {
    @Override
    protected final MapCodec<? extends BiomeSource> codec() {
        return mapCodec();
    }
    //?} else {
    /*@Override
    protected final com.mojang.serialization.Codec<? extends BiomeSource> codec() {
        return Dimensions.legacyCodec(mapCodec());
    }
    *///?}

    /**
     * Gives this biome source the level seed. Called by its {@link CompatChunkGenerator}; a source built outside a level
     * (a GameTest, a preview) may call it itself. Returns this biome source. {@link #seeded(long)} runs on every call.
     */
    public CompatBiomeSource useSeed(long levelSeed) {
        this.seed = levelSeed;
        this.seeded = true;
        seeded(levelSeed);
        return this;
    }

    /** Called when the seed is set (to build seed-dependent state); nothing by default. */
    protected void seeded(long levelSeed) {}

    /** Whether the level seed is known yet. */
    protected final boolean isSeeded() {
        return seeded;
    }

    /** The level seed; available once the generator has set it (before any chunk). */
    protected final long seed() {
        if (!seeded) throw new IllegalStateException(getClass().getName() + " asked for its seed before its generator set it");
        return seed;
    }
}
