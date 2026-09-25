package dev.golem.core.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * Vanilla crafting recipe JSON in the node's own format, for recipes minted at runtime where no build
 * step converts a 26.1 declaration downward. Ingredients are {@code "ns:item"} or {@code "#ns:tag"}.
 */
public final class RecipeJson {

    private RecipeJson() {}

    public static JsonObject shapeless(String result, int count, List<String> ingredients) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shapeless");
        JsonArray list = new JsonArray();
        ingredients.forEach(ingredient -> list.add(ingredient(ingredient)));
        recipe.add("ingredients", list);
        recipe.add("result", result(result, count));
        return recipe;
    }

    /** {@code pattern} rows as in a recipe file; {@code key} maps each symbol to an ingredient. */
    public static JsonObject shaped(String result, int count, List<String> pattern, Map<Character, String> key) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shaped");
        JsonArray rows = new JsonArray();
        pattern.forEach(rows::add);
        recipe.add("pattern", rows);
        JsonObject keys = new JsonObject();
        key.forEach((symbol, ingredient) -> keys.add(String.valueOf(symbol), ingredient(ingredient)));
        recipe.add("key", keys);
        recipe.add("result", result(result, count));
        return recipe;
    }

    private static JsonElement ingredient(String ingredient) {
        //? if >=1.21.2 {
        return new com.google.gson.JsonPrimitive(ingredient);
        //?} else {
        /*JsonObject object = new JsonObject();
        if (ingredient.startsWith("#")) object.addProperty("tag", ingredient.substring(1));
        else object.addProperty("item", ingredient);
        return object;
        *///?}
    }

    private static JsonObject result(String result, int count) {
        JsonObject object = new JsonObject();
        //? if >=1.20.5 {
        object.addProperty("id", result);
        //?} else {
        /*object.addProperty("item", result);
        *///?}
        object.addProperty("count", count);
        return object;
    }
}
