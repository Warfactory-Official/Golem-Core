package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Biome modifiers declared in code, for every loader.
 *
 * <p>A biome modifier is a Forge-family idea with no vanilla counterpart. NeoForge and Forge spell it under
 * different namespaces ({@code neoforge:add_features} against {@code forge:add_features}) with otherwise the
 * same file. Fabric has no file: it adds features through {@code BiomeModifications} in code.
 *
 * <p>One declaration serves all three. Both forge-family spellings are emitted as data and each target's build
 * drops the one it does not use; the same declaration is recorded here at run time, where
 * {@link dev.golem.core.world.BiomeFeatures#install()} hands it to Fabric's API.
 *
 * <p>{@code features} is an ordered list: the game keeps a step's features in the order a biome lists them, so
 * a family whose members overwrite each other (a vein placed into another vein) keeps its declared order.
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

    /**
     * One declared spawn: {@code entity} (an entity id) joins the natural spawns of every biome named by {@code biomes}
     * (a biome id, a {@code #}-prefixed tag, or {@link #ALL_BIOMES}) except {@code excluded} (a biome id, or null), with
     * a weight and group size. The spawn category is the entity type's own, as on every loader.
     */
    public record SpawnDeclaration(String namespace, String name, String biomes, String excluded, String entity,
                                   int weight, int minCount, int maxCount) {}

    /** Every biome of every dimension (1.7 {@code BiomeGenBase.getBiomeGenArray()}). */
    public static final String ALL_BIOMES = "*";

    private static final List<SpawnDeclaration> SPAWNS = Collections.synchronizedList(new ArrayList<>());

    /**
     * Adds a natural spawn (Forge-family {@code add_spawns}; Fabric {@code BiomeModifications} via
     * {@link dev.golem.core.world.BiomeFeatures#install()}). The spawner entry is {@code {type, weight, minCount,
     * maxCount}} on every node (26.1's weighted codec inlines the spawner data).
     */
    public static void addSpawns(String namespace, String name, String biomes, String excluded, String entity,
                                 int weight, int minCount, int maxCount) {
        if (namespace == null || namespace.isEmpty() || name == null || name.isEmpty()) {
            throw new DejsonException("a biome modifier needs a namespace and a name");
        }
        if (!ALL_BIOMES.equals(biomes)) Models.requireId(biomes.startsWith("#") ? biomes.substring(1) : biomes, "biome");
        if (excluded != null) Models.requireId(excluded, "biome");
        Models.requireId(entity, "entity");
        if (weight < 0 || minCount < 1 || maxCount < minCount) {
            throw new DejsonException(name + " has an invalid weight or group size");
        }
        for (String loader : new String[] {"neoforge", "forge"}) {
            JsonObject spawner = new JsonObject();
            // Flat on every node: 26.1's Weighted<SpawnerData> codec inlines the data's fields beside "weight".
            spawner.addProperty("type", entity);
            spawner.addProperty("weight", weight);
            spawner.addProperty("minCount", minCount);
            spawner.addProperty("maxCount", maxCount);
            JsonObject modifier = new JsonObject();
            modifier.addProperty("type", loader + ":add_spawns");
            if (ALL_BIOMES.equals(biomes)) {
                JsonObject set = new JsonObject();
                if (excluded != null) {
                    set.addProperty("type", loader + ":not");
                    set.addProperty("value", excluded);
                } else {
                    set.addProperty("type", loader + ":any");
                }
                modifier.add("biomes", set);
            } else {
                if (excluded != null) throw new DejsonException(name + ": an exclusion needs ALL_BIOMES");
                modifier.addProperty("biomes", biomes);
            }
            modifier.add("spawners", spawner);
            Data.declare("data/" + namespace + "/" + loader + "/biome_modifier/" + name + ".json", modifier);
        }
        SPAWNS.add(new SpawnDeclaration(namespace, name, biomes, excluded, entity, weight, minCount, maxCount));
    }

    /** Every declared spawn, in declaration order. */
    public static List<SpawnDeclaration> declaredSpawns() {
        synchronized (SPAWNS) {
            return List.copyOf(SPAWNS);
        }
    }

    /** Everything declared so far, in declaration order. */
    public static List<Declaration> declared() {
        synchronized (DECLARED) {
            return List.copyOf(DECLARED);
        }
    }
}
