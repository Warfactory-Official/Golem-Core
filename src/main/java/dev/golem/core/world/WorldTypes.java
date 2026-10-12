package dev.golem.core.world;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * A mod's world type, 1.7's {@code WorldType}: a choice on the create-world screen that the world remembers, so the mod
 * can ask later which one it was made with (hbm-space's "The Martian" starts players on Duna).
 *
 * <p>Modern worlds are made from a world preset ({@code worldgen/world_preset}), but the preset's id is not saved: only
 * its dimensions are, and a mod's own dimensions always come from its datapack, never from the preset. So a world type
 * here is a preset whose overworld is vanilla's in every way but one: its {@code multi_noise} biome source names the
 * parameter list {@code <namespace>:<name>} instead of {@code minecraft:overworld}, and that list (authored,
 * {@code worldgen/multi_noise_biome_source_parameter_list/<name>.json}) is {@code {"preset": "minecraft:overworld"}}:
 * the same biomes, terrain and structures, and a name that the overworld's generator saves in the world.
 *
 * <p>Authored JSON, all in the 26.1 shape, which the three versions share: the preset
 * ({@code data/<namespace>/worldgen/world_preset/<name>.json}, vanilla's {@code normal} with that one biome source), the
 * parameter list, the tag {@code minecraft:normal} (or {@code extended}) of {@code worldgen/world_preset} to list it on
 * the screen, and the name {@code generator.<namespace>.<name>}. Code: {@link #declare} at mod construction (vanilla
 * would call such an overworld experimental and ask before creating or opening the world; a declared one is stable,
 * {@code MixinWorldDimensionsStability}) and {@link #is} at run time.
 */
public final class WorldTypes {

    private WorldTypes() {}

    // "<namespace>:<name>" of every declared world type
    private static final Set<String> DECLARED = ConcurrentHashMap.newKeySet();

    /** Declares the world type {@code <namespace>:<name>} (see the class comment). Call at mod construction. */
    public static void declare(String namespace, String name) {
        DECLARED.add(namespace + ":" + name);
    }

    /** Whether the server's world was made with the world type {@code <namespace>:<name>}. */
    public static boolean is(MinecraftServer server, String namespace, String name) {
        var overworld = server.overworld();
        return overworld != null && overworld.getChunkSource().getGenerator().getBiomeSource() instanceof MultiNoiseBiomeSource source
                && source.stable(key(namespace + ":" + name));
    }

    /** True for an overworld stem that is vanilla's but for a declared world type's parameter list (stable, not experimental). */
    public static boolean isStableOverworld(ResourceKey<LevelStem> key, LevelStem stem) {
        if (DECLARED.isEmpty() || !key.equals(LevelStem.OVERWORLD) || !stem.type().is(BuiltinDimensionTypes.OVERWORLD)) return false;
        if (!(stem.generator().getBiomeSource() instanceof MultiNoiseBiomeSource source)) return false;
        for (String id : DECLARED) if (source.stable(key(id))) return true;
        return false;
    }

    private static ResourceKey<MultiNoiseBiomeSourceParameterList> key(String id) {
        return ResourceKey.create(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, dev.golem.core.Ids.parse(id));
    }
}
