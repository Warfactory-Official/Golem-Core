package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Worldgen biomes declared in code.
 *
 * <p>The file is 26.1's shape: sky, fog and water-fog colours are environment attributes and every
 * colour is a {@code #rrggbb} string. {@code legacy-biomes} rewrites the emitted file to the older
 * codec (colours back into {@code effects} as integers, carvers as a map) in the processed resources,
 * which is where every era difference in this layer is resolved.
 *
 * <pre>{@code
 * Biomes.biome("hbm", "crater").temperature(0.5).downfall(0.0).precipitation(false)
 *       .sky(0x525A52).fog(0x525A52).waterFog(0x050533)
 *       .water(0xE0FFAE).grass(0x505050).foliage(0x6A7039)
 *       .spawn(Biomes.MONSTER, "minecraft:zombie", 95, 4, 4)
 *       .register();
 * }</pre>
 *
 * <p>Features and carvers are not declared per biome here: a biome that only exists to be placed by
 * the mod's own code has none, and the file then lists one empty step per generation stage.
 */
public final class Biomes {

    public static final String MONSTER = "monster";
    public static final String CREATURE = "creature";
    public static final String AMBIENT = "ambient";
    public static final String AXOLOTLS = "axolotls";
    public static final String UNDERGROUND_WATER_CREATURE = "underground_water_creature";
    public static final String WATER_CREATURE = "water_creature";
    public static final String WATER_AMBIENT = "water_ambient";
    public static final String MISC = "misc";

    private static final List<String> CATEGORIES = List.of(AMBIENT, AXOLOTLS, CREATURE, MISC, MONSTER,
            UNDERGROUND_WATER_CREATURE, WATER_AMBIENT, WATER_CREATURE);

    /** One list per {@code GenerationStep.Decoration}, which has eleven stages on every target here. */
    private static final int FEATURE_STEPS = 11;

    private Biomes() {}

    public static Biome biome(String namespace, String name) {
        return new Biome(namespace, name);
    }

    public static final class Biome {

        private final String namespace;
        private final String name;
        private double temperature = 0.5;
        private double downfall = 0.5;
        private boolean precipitation = true;
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private final Map<String, String> effects = new LinkedHashMap<>();
        private final Map<String, JsonArray> spawners = new LinkedHashMap<>();

        private Biome(String namespace, String name) {
            Models.requireId(namespace + ":" + name, "biome");
            this.namespace = namespace;
            this.name = name;
            for (String category : CATEGORIES) {
                spawners.put(category, new JsonArray());
            }
        }

        public Biome temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Biome downfall(double downfall) {
            this.downfall = downfall;
            return this;
        }

        public Biome precipitation(boolean precipitation) {
            this.precipitation = precipitation;
            return this;
        }

        public Biome sky(int rgb) {
            attributes.put("minecraft:visual/sky_color", colour(rgb));
            return this;
        }

        public Biome fog(int rgb) {
            attributes.put("minecraft:visual/fog_color", colour(rgb));
            return this;
        }

        public Biome waterFog(int rgb) {
            attributes.put("minecraft:visual/water_fog_color", colour(rgb));
            return this;
        }

        public Biome water(int rgb) {
            effects.put("water_color", colour(rgb));
            return this;
        }

        public Biome grass(int rgb) {
            effects.put("grass_color", colour(rgb));
            return this;
        }

        public Biome foliage(int rgb) {
            effects.put("foliage_color", colour(rgb));
            return this;
        }

        public Biome spawn(String category, String entity, int weight, int minCount, int maxCount) {
            JsonArray list = spawners.get(category);
            if (list == null) {
                throw new DejsonException("'" + category + "' is not a spawn category");
            }
            Models.requireId(entity, "spawned entity");
            if (weight <= 0 || minCount < 1 || maxCount < minCount) {
                throw new DejsonException(entity + " spawn weight " + weight + ", count " + minCount + ".."
                        + maxCount + " is not a spawn entry");
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("type", entity);
            entry.addProperty("weight", weight);
            entry.addProperty("minCount", minCount);
            entry.addProperty("maxCount", maxCount);
            list.add(entry);
            return this;
        }

        public JsonObject register() {
            JsonObject json = new JsonObject();
            JsonObject attributeJson = new JsonObject();
            attributes.forEach(attributeJson::addProperty);
            json.add("attributes", attributeJson);
            json.add("carvers", new JsonArray());
            json.addProperty("downfall", downfall);
            JsonObject effectJson = new JsonObject();
            effects.forEach(effectJson::addProperty);
            json.add("effects", effectJson);
            JsonArray features = new JsonArray();
            for (int step = 0; step < FEATURE_STEPS; step++) {
                features.add(new JsonArray());
            }
            json.add("features", features);
            json.addProperty("has_precipitation", precipitation);
            json.add("spawn_costs", new JsonObject());
            JsonObject spawnerJson = new JsonObject();
            spawners.forEach((category, list) -> spawnerJson.add(category, list.deepCopy()));
            json.add("spawners", spawnerJson);
            json.addProperty("temperature", temperature);
            return Data.declare("data/" + namespace + "/worldgen/biome/" + name + ".json", json);
        }

        private static String colour(int rgb) {
            if ((rgb & 0xFF000000) != 0) {
                throw new DejsonException(String.format("biome colour %08X carries alpha; give RGB", rgb));
            }
            return String.format("#%06x", rgb);
        }
    }
}
