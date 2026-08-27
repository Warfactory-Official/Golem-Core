package dev.golem.core.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Biome construction, mutation, and tracking synchronization across the supported versions. */
public final class Biomes {

    private Biomes() {}

    /** Creates a key for a datapack biome registry entry. */
    public static ResourceKey<Biome> key(String namespace, String path) {
        return ResourceKey.create(Registries.BIOME, dev.golem.core.Ids.of(namespace, path));
    }

    /** The vanilla noise used by the legacy biome grass-colour overrides. */
    public static boolean legacyGrassColorNoise(double x, double z) {
        return net.minecraft.world.level.biome.Biome.BIOME_INFO_NOISE.getValue(
                x * 0.225D, z * 0.225D, false) < -0.1D;
    }

    /** One requested replacement of the biome column containing a block position. */
    public record Change(int blockX, int blockZ, Holder<Biome> target,
                         Predicate<Holder<Biome>> preserve) {
        public Change {
            Objects.requireNonNull(target);
            Objects.requireNonNull(preserve);
        }
    }

    /** Resolves a registered biome key from the live world's dynamic registry access. */
    public static Holder<Biome> holder(RegistryAccess registries, ResourceKey<Biome> key) {
        //? if >=26.1 {
        return registries.lookupOrThrow(Registries.BIOME).getOrThrow(key);
        //?} else if >=1.21.1 {
        /*return registries.lookupOrThrow(Registries.BIOME).getOrThrow(key);
        *///?} else {
        /*return registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
        *///?}
    }

    /**
     * Applies a batch of column replacements through vanilla's biome fill path.
     *
     * <p>Biome storage is four-by-four-by-four, while the legacy feature requested block columns. The
     * input is therefore resolved in block coordinates but applied to the corresponding quart column.
     * Existing cells are snapshotted first so the resolver never reads a container while rewriting it.
     */
    public static boolean apply(ChunkAccess chunk, List<Change> changes) {
        if (changes.isEmpty()) return false;

        LevelChunkSection[] sections = chunk.getSections();
        Map<Cell, Holder<Biome>> original = new HashMap<>(sections.length * 64);
        int minSectionY = minSectionY(chunk);
        int minQuartX = chunk.getPos().getMinBlockX() >> 2;
        int minQuartZ = chunk.getPos().getMinBlockZ() >> 2;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            int quartY = (minSectionY + sectionIndex) << 2;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    for (int z = 0; z < 4; z++) {
                        original.put(new Cell(minQuartX + x, quartY + y, minQuartZ + z),
                                section.getNoiseBiome(x, y, z));
                    }
                }
            }
        }

        Map<Column, Holder<Biome>> replacements = new HashMap<>();
        for (Change change : changes) {
            Column column = new Column(change.blockX() >> 2, change.blockZ() >> 2);
            Holder<Biome> current = replacements.get(column);
            if (current == null) current = chunk.getNoiseBiome(column.x(), 0, column.z());
            if (!change.preserve().test(current) && !sameBiome(current, change.target())) {
                replacements.put(column, change.target());
            }
        }
        if (replacements.isEmpty()) return false;

        chunk.fillBiomesFromNoise((x, y, z, sampler) -> {
            Holder<Biome> current = original.get(new Cell(x, y, z));
            if (current == null) {
                throw new IllegalStateException("Biome resolver escaped chunk " + chunk.getPos());
            }
            return replacements.getOrDefault(new Column(x, z), current);
        }, null);
        return true;
    }

    /** Sends changed biome containers to players tracking the chunk. Must run on the server thread. */
    public static void resend(ServerLevel level, ChunkAccess chunk) {
        level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
    }

    private static boolean sameBiome(Holder<Biome> first, Holder<Biome> second) {
        return first == second || first.value() == second.value();
    }

    private static int minSectionY(ChunkAccess chunk) {
        //? if >=26.1 {
        return chunk.getMinY() >> 4;
        //?} else {
        /*return chunk.getMinBuildHeight() >> 4;
        *///?}
    }

    private record Cell(int x, int y, int z) {}

    private record Column(int x, int z) {}
}
