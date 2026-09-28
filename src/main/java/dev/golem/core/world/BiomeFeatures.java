package dev.golem.core.world;

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

    private static boolean installed;

    private BiomeFeatures() {}

    /** Hands every declared biome modifier to the loader. Idempotent; call during mod initialisation. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        //? if fabric {
        /*for (BiomeModifiers.Declaration declaration : BiomeModifiers.declared()) {
            java.util.function.Predicate<net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext> selector;
            String biomes = declaration.biomes();
            if (biomes.startsWith("#")) {
                selector = net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.BIOME, dev.golem.core.Ids.parse(biomes.substring(1))));
            } else {
                selector = net.fabricmc.fabric.api.biome.v1.BiomeSelectors.includeByKey(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.BIOME, dev.golem.core.Ids.parse(biomes)));
            }
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
            // feature list in the same order — which is what seeds each feature's placement. (addFeature per feature
            // would name each modification after its feature and re-sort the list alphabetically.)
            net.fabricmc.fabric.api.biome.v1.BiomeModifications.create(dev.golem.core.Ids.of(declaration.namespace(), declaration.name()))
                    .add(net.fabricmc.fabric.api.biome.v1.ModificationPhase.ADDITIONS, selector, context -> {
                        for (var key : keys) context.getGenerationSettings().addFeature(step, key);
                    });
        }
        *///?}
    }
}
