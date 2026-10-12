package dev.golem.core.world;

import com.mojang.serialization.MapCodec;
import dev.golem.core.registry.DeferredRegister;
import dev.golem.core.registry.RegistryObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * A mod's dimensions. The dimension, its type and its biomes are authored JSON in the 26.1 shape
 * ({@code data/<ns>/dimension}, {@code dimension_type}, {@code worldgen/biome}); the shared build layer converts
 * dimension types and biomes for older nodes ({@code legacy-dimensions}, {@code legacy-biomes}). Code registers only
 * what JSON cannot name: chunk generator types ({@link #chunkGenerator}, for a {@link CompatChunkGenerator}) and biome
 * source types ({@link #biomeSource}, for a {@link CompatBiomeSource}).
 *
 * <p>Vanilla calls any world with a dimension other than its own three "experimental" and asks before creating or
 * opening it. A mod states that its own dimensions are ordinary content with {@link #dimension}; Fabric API already
 * treats every mod dimension that way, Golem Core gives NeoForge and Forge the same rule for the dimensions a mod names.
 */
public final class Dimensions {

    /** A registered chunk generator type, the {@code "type"} of a dimension's generator in JSON. */
    public record GeneratorType(String namespace, String name, RegistryObject<?> handle) {}

    /** A registered biome source type, the {@code "type"} of a generator's {@code biome_source} in JSON. */
    public record BiomeSourceType(String namespace, String name, RegistryObject<?> handle) {}

    // One register per namespace, created on first use; guarded by itself.
    private static final Map<String, DeferredRegister<?>> GENERATORS = new HashMap<>();

    // One register per namespace, created on first use; guarded by itself.
    private static final Map<String, DeferredRegister<?>> BIOME_SOURCES = new HashMap<>();

    // "<namespace>:<path>" of every dimension a mod declared with dimension(); read by MixinWorldDimensionsStability.
    private static final Set<String> STABLE = ConcurrentHashMap.newKeySet();

    private Dimensions() {}

    /**
     * Registers {@code codec} as the chunk generator type {@code <namespace>:<name>}. Registries hold a
     * {@code MapCodec} from 1.20.5 and a plain {@code Codec} below; this registers whichever the node needs.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static GeneratorType chunkGenerator(String namespace, String name,
                                               Supplier<? extends MapCodec<? extends ChunkGenerator>> codec) {
        DeferredRegister register;
        synchronized (GENERATORS) {
            register = GENERATORS.computeIfAbsent(namespace,
                    ns -> DeferredRegister.create(() -> (ResourceKey) Registries.CHUNK_GENERATOR, ns));
        }
        //? if >=1.20.5 {
        RegistryObject<?> handle = register.register(name, (Supplier) codec);
        //?} else {
        /*RegistryObject<?> handle = register.register(name, (Supplier) () -> legacyCodec(codec.get()));
        *///?}
        return new GeneratorType(namespace, name, handle);
    }

    /**
     * Registers {@code codec} as the biome source type {@code <namespace>:<name>}. Registries hold a {@code MapCodec}
     * from 1.20.5 and a plain {@code Codec} below (the same instance {@link CompatBiomeSource#codec()} hands out, by
     * {@link #legacyCodec}); this registers whichever the node needs.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BiomeSourceType biomeSource(String namespace, String name,
                                              Supplier<? extends MapCodec<? extends BiomeSource>> codec) {
        DeferredRegister register;
        synchronized (BIOME_SOURCES) {
            register = BIOME_SOURCES.computeIfAbsent(namespace,
                    ns -> DeferredRegister.create(() -> (ResourceKey) Registries.BIOME_SOURCE, ns));
        }
        //? if >=1.20.5 {
        RegistryObject<?> handle = register.register(name, (Supplier) codec);
        //?} else {
        /*RegistryObject<?> handle = register.register(name, (Supplier) () -> legacyCodec(codec.get()));
        *///?}
        return new BiomeSourceType(namespace, name, handle);
    }

    /**
     * Declares {@code <namespace>:<path>} as a mod dimension: a world that has it is not "experimental" (no confirmation
     * screen on create or open). Returns its level key. Call it once, at mod construction.
     */
    public static ResourceKey<Level> dimension(String namespace, String path) {
        STABLE.add(namespace + ":" + path);
        return level(namespace, path);
    }

    /** Whether a mod declared the dimension {@code key} with {@link #dimension}. */
    public static boolean isDeclared(ResourceKey<?> key) {
        return STABLE.contains(dev.golem.core.Ids.keyId(key).toString());
    }

    // MapCodec -> the one Codec registered for it below 1.20.5, by identity
    private static final Map<MapCodec<?>, com.mojang.serialization.Codec<?>> LEGACY_CODECS = new java.util.IdentityHashMap<>();

    /**
     * Below 1.20.5 the generator registry holds a plain {@code Codec}, looked up by identity when a level saves: the
     * registration and every generator's {@code codec()} must hand out the same instance, never a fresh
     * {@code mapCodec.codec()} (an unknown instance makes the save fail and the world unloadable).
     */
    @SuppressWarnings("unchecked")
    public static synchronized <T> com.mojang.serialization.Codec<T> legacyCodec(MapCodec<T> codec) {
        return (com.mojang.serialization.Codec<T>) LEGACY_CODECS.computeIfAbsent(codec, MapCodec::codec);
    }

    /** The level key of a mod dimension, {@code <namespace>:<path>}. */
    public static ResourceKey<Level> level(String namespace, String path) {
        return ResourceKey.create(Registries.DIMENSION, dev.golem.core.Ids.of(namespace, path));
    }
}
