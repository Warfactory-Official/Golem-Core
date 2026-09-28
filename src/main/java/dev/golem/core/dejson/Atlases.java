package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Atlas source lists: which texture directories are stitched into which atlas.
 *
 * <p>A mod whose textures sit outside the directory vanilla stitches by default adds its directory
 * here. The files are 26.1's; below 1.21.4 there is no {@code items} atlas, and {@code legacy-assets}
 * folds whatever is declared for it into {@code blocks} in the processed resources.
 *
 * <pre>{@code Atlases.directories("minecraft", "blocks", "blocks", "blocks/");}</pre>
 */
public final class Atlases {

    private Atlases() {}

    /**
     * {@code assets/<namespace>/atlases/<atlas>.json} with one {@code minecraft:directory} source per
     * pair of {@code source}, {@code prefix} arguments.
     */
    public static JsonObject directories(String namespace, String atlas, String... sourceThenPrefix) {
        if (sourceThenPrefix.length == 0 || sourceThenPrefix.length % 2 != 0) {
            throw new DejsonException("atlas " + atlas + " needs source/prefix pairs");
        }
        JsonArray sources = new JsonArray();
        for (int index = 0; index < sourceThenPrefix.length; index += 2) {
            JsonObject source = new JsonObject();
            source.addProperty("type", "minecraft:directory");
            source.addProperty("source", sourceThenPrefix[index]);
            source.addProperty("prefix", sourceThenPrefix[index + 1]);
            sources.add(source);
        }
        JsonObject json = new JsonObject();
        json.add("sources", sources);
        return Data.declareAsset("assets/" + namespace + "/atlases/" + atlas + ".json", json);
    }
}
