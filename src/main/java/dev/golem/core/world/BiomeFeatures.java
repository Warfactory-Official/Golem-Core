package dev.golem.core.world;

import dev.golem.core.Ids;
import dev.golem.core.registry.Entities;
import java.util.function.Predicate;
//? if fabric {
/*import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
*///?}
//? if fabric {
/*import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
*///?}
//? if fabric {
/*import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
*///?}
//? if fabric {
/*import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
*///?}
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import dev.golem.core.dejson.BiomeModifiers;

/**
 * Applies the {@link BiomeModifiers} declarations on the loader that has no biome-modifier files.
 *
 * <p>NeoForge and Forge read the {@code biome_modifier} JSON the declarations emit, so on those loaders this
 * does nothing. Fabric reads no such file; it adds placed features through {@code BiomeModifications} in code.
 * Calling {@link #install()} once, after the declaring classes have run, gives Fabric exactly the list the other
 * two loaders load from data: same biomes (id or tag), same step, same features in the same order.
 */
public final class BiomeFeatures {

    /** How many declarations earlier calls handed over; a later call (another mod's) hands over only the newer ones. */
    private static int installedFeatures;
    private static int installedSpawns;
    private static int installedSpawnRemovals;

    private BiomeFeatures() {}

    /**
     * Hands every declared biome modifier not handed over yet to the loader. Idempotent; call during mod initialisation,
     * after declaring. Each mod calls it after its own declarations (HBM, then an addon that declares its own).
     */
    public static synchronized void install() {
        //? if fabric {
        /*java.util.List<BiomeModifiers.Declaration> features = BiomeModifiers.declared();
        java.util.List<BiomeModifiers.SpawnDeclaration> spawns = BiomeModifiers.declaredSpawns();
        var spawnRemovals = BiomeModifiers.declaredSpawnRemovals();
        int fromFeature = installedFeatures;
        int fromSpawn = installedSpawns;
        int fromSpawnRemoval = installedSpawnRemovals;
        installedFeatures = features.size();
        installedSpawns = spawns.size();
        installedSpawnRemovals = spawnRemovals.size();
        for (BiomeModifiers.Declaration declaration : features.subList(fromFeature, features.size())) {
            var selector = selectBiomes(declaration.biomes());
            if (declaration.excluded() != null) selector = selector.and(selectBiomes(declaration.excluded()).negate());
            net.minecraft.world.level.levelgen.GenerationStep.Decoration step =
                    net.minecraft.world.level.levelgen.GenerationStep.Decoration.valueOf(
                            declaration.step().toUpperCase(java.util.Locale.ROOT));
            java.util.List<net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.placement.PlacedFeature>> keys =
                    new java.util.ArrayList<>();
            for (String feature : declaration.features()) {
                keys.add(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE,
                        dev.golem.core.Ids.parse(feature)));
            }
            // One modification per declaration, named like the forge-family modifier file. Fabric applies modifications
            // sorted by id and the forge-family loaders apply modifier files sorted by id, so both end with the same
            // feature list in the same order, which is what seeds each feature's placement. (addFeature per feature
            // would name each modification after its feature and re-sort the list alphabetically.)
            net.fabricmc.fabric.api.biome.v1.BiomeModifications.create(dev.golem.core.Ids.of(declaration.namespace(), declaration.name()))
                    .add(net.fabricmc.fabric.api.biome.v1.ModificationPhase.ADDITIONS, selector, context -> {
                        for (var key : keys) context.getGenerationSettings().addFeature(step, key);
                    });
        }
        for (BiomeModifiers.SpawnDeclaration spawn : spawns.subList(fromSpawn, spawns.size())) {
            var selector = selectBiomes(spawn.biomes());
            if (spawn.excluded() != null) selector = selector.and(selectBiomes(spawn.excluded()).negate());
            // The entity type is resolved when the modification runs (world load), after registration.
            net.fabricmc.fabric.api.biome.v1.BiomeModifications.create(dev.golem.core.Ids.of(spawn.namespace(), spawn.name()))
                    .add(net.fabricmc.fabric.api.biome.v1.ModificationPhase.ADDITIONS, selector, context -> {
                        net.minecraft.world.entity.EntityType<?> type = dev.golem.core.registry.Entities.byId(spawn.entity());
                        //? if >=26.1 {
                        context.getMobSpawnSettings().addSpawn(type.getCategory(),
                                new net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData(type, spawn.minCount(), spawn.maxCount()),
                                spawn.weight());
                        //?} else {
                        /^context.getSpawnSettings().addSpawn(type.getCategory(),
                                new net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData(type, spawn.weight(), spawn.minCount(), spawn.maxCount()));
                        ^///?}
                    });
        }
        for (var removal : spawnRemovals.subList(fromSpawnRemoval, spawnRemovals.size())) {
            BiomeModifications.create(Ids.of(removal.namespace(), removal.name()))
                    .add(ModificationPhase.REMOVALS, selectBiomes(removal.biomes()), context -> {
                        for (var entity : removal.entities()) {
                            var type = Entities.byId(entity);
                            //? if >=26.1 {
                            context.getMobSpawnSettings().removeSpawnsOfEntityType(type);
                            //?} else {
                            /^context.getSpawnSettings().removeSpawnsOfEntityType(type);
                            ^///?}
                        }
                    });
        }
        *///?}
    }

    //? if fabric {
    /*private static Predicate<BiomeSelectionContext> selectBiomes(String biomes) {
        if (BiomeModifiers.ALL_BIOMES.equals(biomes)) return BiomeSelectors.all();
        if (biomes.startsWith("#")) {
            return BiomeSelectors.tag(TagKey.create(
                    Registries.BIOME, Ids.parse(biomes.substring(1))));
        }
        return BiomeSelectors.includeByKey(ResourceKey.create(
                Registries.BIOME, Ids.parse(biomes)));
    }
    *///?}
}
