package dev.golem.core.dejson;

import com.google.gson.JsonObject;

/**
 * Forge-family biome modifiers declared in code.
 *
 * <p>A biome modifier is a Forge-family idea with no vanilla or Fabric counterpart, and the two
 * loaders that have it spell it under different namespaces: {@code neoforge:add_features} against
 * {@code forge:add_features}. The file is otherwise the same, so it is written twice, in two
 * directories, and a change to one of them is silently not a change to the other. That is not a
 * version difference a consumer should be carrying — it is exactly the kind of thing that belongs
 * in the facade layer.
 *
 * <p>Both spellings are emitted from one declaration, and each target's own build then drops the
 * two it does not use — {@code processResources} already excludes the other loaders' data trees so
 * the jar carries no inert files.
 */
public final class BiomeModifiers {

    /** The generation step a feature is added at, matching {@code GenerationStep.Decoration}. */
    public static final String UNDERGROUND_ORES = "underground_ores";
    public static final String LOCAL_MODIFICATIONS = "local_modifications";

    private BiomeModifiers() {}

    /**
     * Adds a placed feature to every biome in {@code biomes}, for both forge-family loaders.
     *
     * @param biomes a biome id or a {@code #}-prefixed biome tag
     */
    public static void addFeatures(String namespace, String name, String biomes, String feature, String step) {
        if (namespace == null || namespace.isEmpty() || name == null || name.isEmpty()) {
            throw new DejsonException("a biome modifier needs a namespace and a name");
        }
        Models.requireId(biomes.startsWith("#") ? biomes.substring(1) : biomes, "biome");
        Models.requireId(feature, "feature");
        if (step == null || step.isEmpty()) {
            throw new DejsonException(name + " is added at no generation step");
        }
        for (String loader : new String[] {"neoforge", "forge"}) {
            JsonObject modifier = new JsonObject();
            modifier.addProperty("type", loader + ":add_features");
            modifier.addProperty("biomes", biomes);
            modifier.addProperty("features", feature);
            modifier.addProperty("step", step);
            Data.declare("data/" + namespace + "/" + loader + "/biome_modifier/" + name + ".json", modifier);
        }
    }
}
