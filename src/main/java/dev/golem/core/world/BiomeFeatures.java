package dev.golem.core.world;

import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * A placed feature added to every biome of a vanilla dimension at runtime. Forge-likes read a
 * data-pack biome modifier ({@link #modifier}, written at {@link DataPaths#biomeModifier}); Fabric
 * has none and takes {@link #inject} before the server starts.
 */
public final class BiomeFeatures {

    private BiomeFeatures() {}

    public enum Dimension {
        OVERWORLD("#minecraft:is_overworld"),
        NETHER("#minecraft:is_nether"),
        END("#minecraft:is_end");

        public final String biomes;

        Dimension(String biomes) {
            this.biomes = biomes;
        }
    }

    /** Fabric: adds {@code feature} at {@code step} ({@code "underground_ores"}, a {@code GenerationStep.Decoration} name). No-op elsewhere. */
    public static void inject(Dimension dimension, String feature, String step) {
        //? if fabric {
        /*var selector = switch (dimension) {
            case OVERWORLD -> net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld();
            case NETHER -> net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInTheNether();
            case END -> net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInTheEnd();
        };
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(selector,
                net.minecraft.world.level.levelgen.GenerationStep.Decoration.valueOf(step.toUpperCase(Locale.ROOT)),
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE,
                        dev.golem.core.Ids.parse(feature)));
        *///?}
    }

    /** The loader's {@code add_features} modifier, or null on Fabric. */
    public static @Nullable JsonObject modifier(Dimension dimension, String feature, String step) {
        //? if neoforge {
        String type = "neoforge:add_features";
        //?} elif forge {
        /*String type = "forge:add_features";
        *///?} else {
        /*String type = null;
        *///?}
        if (type == null) return null;
        JsonObject modifier = new JsonObject();
        modifier.addProperty("type", type);
        modifier.addProperty("biomes", dimension.biomes);
        modifier.addProperty("features", feature);
        modifier.addProperty("step", step.toLowerCase(Locale.ROOT));
        return modifier;
    }
}
