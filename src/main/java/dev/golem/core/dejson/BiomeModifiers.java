package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Biome modifiers declared in code, for every loader.
 *
 * <p>A biome modifier is a Forge-family idea with no vanilla counterpart, and the two loaders that have
 * it spell it under different namespaces: {@code neoforge:add_features} against {@code forge:add_features}.
 * The file is otherwise the same, so it is written twice, in two directories, and a change to one of
 * them is silently not a change to the other. Fabric has no file at all: it adds features through
 * {@code BiomeModifications} in code, which used to be a third, hand-kept copy of the same list.
 *
 * <p>One declaration now serves all three. Both forge-family spellings are emitted as data, and each
 * target's own build drops the one it does not use; the same declaration is also recorded here at run
 * time, where {@link dev.golem.core.world.BiomeFeatures#install()} hands it to Fabric's API. The three
 * lists cannot drift apart because there is only one.
 *
 * <p>{@code features} is written as a list, in order: the game keeps a step's features in the order a
 * biome lists them, so a family whose members overwrite each other (a vein placed into another vein)
 * keeps the order it was declared in.
 */
public final class BiomeModifiers {

    /** The generation steps a feature is added at, matching {@code GenerationStep.Decoration}, in run order. */
    public static final String RAW_GENERATION = "raw_generation";
    public static final String LAKES = "lakes";
    public static final String LOCAL_MODIFICATIONS = "local_modifications";
    public static final String UNDERGROUND_STRUCTURES = "underground_structures";
    public static final String SURFACE_STRUCTURES = "surface_structures";
    public static final String STRONGHOLDS = "strongholds";
    public static final String UNDERGROUND_ORES = "underground_ores";
    public static final String UNDERGROUND_DECORATION = "underground_decoration";
    public static final String FLUID_SPRINGS = "fluid_springs";
    public static final String VEGETAL_DECORATION = "vegetal_decoration";
    public static final String TOP_LAYER_MODIFICATION = "top_layer_modification";

    private static final List<String> STEPS = List.of(RAW_GENERATION, LAKES, LOCAL_MODIFICATIONS, UNDERGROUND_STRUCTURES,
            SURFACE_STRUCTURES, STRONGHOLDS, UNDERGROUND_ORES, UNDERGROUND_DECORATION, FLUID_SPRINGS,
            VEGETAL_DECORATION, TOP_LAYER_MODIFICATION);

    /**
     * One declared modifier: add {@code features} (placed-feature ids, in order) to every biome named by
     * {@code biomes} (a biome id or a {@code #}-prefixed biome tag) at {@code step}.
     */
    public record Declaration(String namespace, String name, String biomes, List<String> features, String step) {}

    private static final List<Declaration> DECLARED = Collections.synchronizedList(new ArrayList<>());

    private BiomeModifiers() {}

    /** Adds one placed feature to every biome in {@code biomes}. */
    public static void addFeatures(String namespace, String name, String biomes, String feature, String step) {
        addFeatures(namespace, name, biomes, List.of(feature), step);
    }

    /**
     * Adds placed features, in order, to every biome in {@code biomes}.
     *
     * @param biomes a biome id or a {@code #}-prefixed biome tag
     */
    public static void addFeatures(String namespace, String name, String biomes, List<String> features, String step) {
        if (namespace == null || namespace.isEmpty() || name == null || name.isEmpty()) {
            throw new DejsonException("a biome modifier needs a namespace and a name");
        }
        Models.requireId(biomes.startsWith("#") ? biomes.substring(1) : biomes, "biome");
        if (features == null || features.isEmpty()) {
            throw new DejsonException(name + " adds no feature");
        }
        for (String feature : features) Models.requireId(feature, "feature");
        if (step == null || !STEPS.contains(step)) {
            throw new DejsonException(name + " is added at an unknown generation step: " + step);
        }
        JsonArray list = new JsonArray();
        features.forEach(list::add);
        for (String loader : new String[] {"neoforge", "forge"}) {
            JsonObject modifier = new JsonObject();
            modifier.addProperty("type", loader + ":add_features");
            modifier.addProperty("biomes", biomes);
            if (features.size() == 1) {
                modifier.addProperty("features", features.get(0));
            } else {
                modifier.add("features", list.deepCopy());
            }
            modifier.addProperty("step", step);
            Data.declare("data/" + namespace + "/" + loader + "/biome_modifier/" + name + ".json", modifier);
        }
        DECLARED.add(new Declaration(namespace, name, biomes, List.copyOf(features), step));
    }

    /** Everything declared so far, in declaration order. */
    public static List<Declaration> declared() {
        synchronized (DECLARED) {
            return List.copyOf(DECLARED);
        }
    }
}
