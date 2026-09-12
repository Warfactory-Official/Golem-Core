package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The recipes whose shape is fixed, declared in code.
 *
 * <p>Most recipes are a design and belong in a file: a pattern somebody chose, with ingredients
 * somebody balanced, and nothing about the file is boilerplate except its punctuation. Three
 * families are not like that. A block that packs from nine of something, the same block unpacking
 * back into nine, and a furnace turning one item into another are each a fixed arrangement around
 * two ids, and writing the arrangement out every time buys nothing.
 *
 * <p>So this covers exactly those three and nothing else. A shaped recipe with a real pattern stays
 * a file, because the pattern is the recipe.
 *
 * <p>The recipe id is passed rather than derived. Recipe ids are visible — the recipe book
 * remembers them, JEI bookmarks them, {@code /recipe} names them — so a migration that renamed them
 * would be a migration that reset people's progress.
 */
public final class Crafting {

    private Crafting() {}

    /** Nine of one ingredient in a filled grid, making one result. */
    public static JsonObject compress(String namespace, String id, String ingredient, String result) {
        JsonArray pattern = new JsonArray();
        pattern.add("###");
        pattern.add("###");
        pattern.add("###");
        JsonObject key = new JsonObject();
        key.addProperty("#", requireIngredient(ingredient));

        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shaped");
        recipe.addProperty("category", "misc");
        recipe.add("key", key);
        recipe.add("pattern", pattern);
        recipe.add("result", result(result, 1));
        return declare(namespace, id, recipe);
    }

    /** One ingredient, shapeless, making nine of the result. */
    public static JsonObject decompress(String namespace, String id, String ingredient, String result) {
        JsonArray ingredients = new JsonArray();
        ingredients.add(requireIngredient(ingredient));

        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shapeless");
        recipe.addProperty("category", "misc");
        recipe.add("ingredients", ingredients);
        recipe.add("result", result(result, 9));
        return declare(namespace, id, recipe);
    }

    /** An ordinary furnace recipe, at vanilla's own two hundred ticks. */
    public static JsonObject smelt(String namespace, String id, String ingredient, String result,
                                   double experience) {
        return smelt(namespace, id, ingredient, result, experience, 1);
    }

    /** The same, yielding more than one — a crystal smelting down into several ingots. */
    public static JsonObject smelt(String namespace, String id, String ingredient, String result,
                                   double experience, int count) {
        if (experience < 0) {
            throw new DejsonException(id + " smelts for " + experience + " experience");
        }
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:smelting");
        recipe.addProperty("category", "misc");
        recipe.addProperty("cookingtime", 200);
        recipe.addProperty("experience", experience);
        recipe.addProperty("ingredient", requireIngredient(ingredient));
        recipe.add("result", result(result, count));
        return declare(namespace, id, recipe);
    }

    /** The path a recipe lives at. */
    public static String path(String namespace, String id) {
        Models.requireId(namespace + ":" + id, "recipe");
        return "data/" + namespace + "/recipe/" + id + ".json";
    }

    private static JsonObject declare(String namespace, String id, JsonObject recipe) {
        return Data.declare(path(namespace, id), recipe);
    }

    private static JsonObject result(String id, int count) {
        Models.requireId(id, "result");
        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        if (count != 1) {
            result.addProperty("count", count);
        }
        return result;
    }

    private static String requireIngredient(String ingredient) {
        Models.requireId(ingredient.startsWith("#") ? ingredient.substring(1) : ingredient, "ingredient");
        return ingredient;
    }
}
