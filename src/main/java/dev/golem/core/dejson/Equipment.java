package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Equipment assets: which texture a worn armour piece draws on each body layer.
 *
 * <p>The file is 26.1's ({@code assets/<ns>/equipment/<name>.json}). Older targets have no equipment
 * layer and find armour textures by path instead; {@code legacy-assets} reads the emitted file and
 * copies each named texture to the path those targets look up, so one declaration serves both eras.
 *
 * <pre>{@code
 * Equipment.humanoid("hbm", "steel", "hbm:steel");   // body and leggings layers
 * Equipment.of("hbm", "goggles").layer(Equipment.HUMANOID, "hbm:goggles").register();
 * }</pre>
 */
public final class Equipment {

    public static final String HUMANOID = "humanoid";
    public static final String HUMANOID_LEGGINGS = "humanoid_leggings";

    private final String namespace;
    private final String name;
    private final Map<String, String> layers = new LinkedHashMap<>();

    private Equipment(String namespace, String name) {
        Models.requireId(namespace + ":" + name, "equipment asset");
        this.namespace = namespace;
        this.name = name;
    }

    public static Equipment of(String namespace, String name) {
        return new Equipment(namespace, name);
    }

    /** The common armour set: one texture on the body layer and one on the leggings layer. */
    public static JsonObject humanoid(String namespace, String name, String texture) {
        return of(namespace, name).layer(HUMANOID, texture).layer(HUMANOID_LEGGINGS, texture).register();
    }

    public Equipment layer(String layer, String texture) {
        Models.requireId(texture, "equipment texture");
        if (layers.putIfAbsent(layer, texture) != null) {
            throw new DejsonException("equipment " + name + " layer '" + layer + "' declared twice");
        }
        return this;
    }

    public JsonObject register() {
        if (layers.isEmpty()) {
            throw new DejsonException("equipment " + name + " declares no layers");
        }
        JsonObject json = new JsonObject();
        JsonObject byLayer = new JsonObject();
        layers.forEach((layer, texture) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("texture", texture);
            JsonArray list = new JsonArray();
            list.add(entry);
            byLayer.add(layer, list);
        });
        json.add("layers", byLayer);
        return Data.declareAsset("assets/" + namespace + "/equipment/" + name + ".json", json);
    }
}
