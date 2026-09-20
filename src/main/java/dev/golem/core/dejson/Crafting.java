package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Crafting, smelting and the component-bearing recipes, declared in code.
 *
 * <p>Three of these families carry no decision at all: a block that packs from nine of something,
 * the same block unpacking back into nine, and a furnace turning one item into another are each a
 * fixed arrangement around two ids. The rest do carry a decision — a pattern somebody chose, with
 * ingredients somebody balanced — and this vocabulary is built so that the decision is all that is
 * left at the call site. {@code shaped} takes the pattern and the symbols and nothing else; the
 * type, the category and the punctuation are the declaration's to supply.
 *
 * <p>Every shape the emitted file can take is checked here rather than at load: a pattern wider
 * than three, a ragged one, a symbol the key does not define, a key symbol the pattern never uses.
 * Those are the mistakes a hand-written recipe made silently — the file parsed, the recipe simply
 * never appeared in the book — and they are decidable from the declaration alone.
 *
 * <p>The recipe id is passed rather than derived. Recipe ids are visible — the recipe book
 * remembers them, JEI bookmarks them, {@code /recipe} names them — so a migration that renamed them
 * would be a migration that reset people's progress.
 */
public final class Crafting {

    private static final int GRID = 3;

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
        JsonObject recipe = cooking("minecraft:smelting", id, experience);
        recipe.addProperty("ingredient", requireIngredient(ingredient));
        recipe.add("result", result(result, count));
        return declare(namespace, id, recipe);
    }

    /**
     * A shaped recipe: a pattern, one ingredient per symbol used in it, and a result.
     *
     * <p>The symbols follow the pattern as alternating pairs, the way the file spells them —
     * {@code rows("SSS", " C ", " C "), "S", "hbm:plate_steel", "C", "#c:ingots/copper"}.
     */
    public static JsonObject shaped(String namespace, String id, String result,
                                    String[] pattern, String... key) {
        return shaped(namespace, id, stack(result), pattern, key);
    }

    /** The same, yielding more than one. */
    public static JsonObject shaped(String namespace, String id, String result, int count,
                                    String[] pattern, String... key) {
        return shaped(namespace, id, stack(result, count), pattern, key);
    }

    /** The same, where the result carries components. */
    public static JsonObject shaped(String namespace, String id, Stack result,
                                    String[] pattern, String... key) {
        List<Key> keys = new ArrayList<>();
        if (key.length % 2 != 0) {
            throw new DejsonException(id + " has a symbol with no ingredient: "
                    + key[key.length - 1]);
        }
        for (int index = 0; index < key.length; index += 2) {
            keys.add(new Key(key[index], null, requireIngredient(key[index + 1])));
        }
        return shapedRecipe("minecraft:crafting_shaped", namespace, id, result, pattern, keys);
    }

    /** A shapeless recipe: up to nine ingredients in any arrangement, and a result. */
    public static JsonObject shapeless(String namespace, String id, String result,
                                       String... ingredients) {
        return shapeless(namespace, id, stack(result), ingredients);
    }

    /** The same, yielding more than one. */
    public static JsonObject shapeless(String namespace, String id, String result, int count,
                                       String... ingredients) {
        return shapeless(namespace, id, stack(result, count), ingredients);
    }

    /** The same, where the result carries components. */
    public static JsonObject shapeless(String namespace, String id, Stack result,
                                       String... ingredients) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shapeless");
        recipe.addProperty("category", "misc");
        recipe.add("ingredients", ingredientList(id, ingredients));
        recipe.add("result", result.json(id));
        return declare(namespace, id, recipe);
    }

    /**
     * A shaped recipe whose ingredients are matched on their components as well as their id.
     *
     * <p>This is the mod's own recipe type, not vanilla's: an ingredient here is an {@link #item}
     * carrying the components the stack in the grid has to have, or has to be without, and the
     * result is usually the same item wearing different ones.
     */
    public static JsonObject componentShaped(String namespace, String id, Stack result,
                                             String[] pattern, Key... key) {
        return shapedRecipe("hbm:component_shaped", namespace, id, result, pattern, List.of(key));
    }

    /** A furnace recipe matched on components, producing a stack that carries its own. */
    public static JsonObject componentSmelt(String namespace, String id, Ingredient ingredient,
                                            Stack result, double experience) {
        JsonObject recipe = cooking("hbm:component_smelting", id, experience);
        recipe.add("ingredient", ingredient.json(id));
        recipe.add("result", result.json(id));
        return declare(namespace, id, recipe);
    }

    /**
     * A shapeless recipe that carries one ingredient's components over to the result.
     *
     * <p>{@code copyFrom} names which of the ingredients is the one carried — the empty rod a fuel
     * rod is assembled in, whose remaining state has to survive the assembly.
     */
    public static JsonObject copyShapeless(String namespace, String id, String result,
                                           String copyFrom, String... ingredients) {
        JsonArray list = ingredientList(id, ingredients);
        String carried = requireIngredient(copyFrom);
        boolean present = false;
        for (int index = 0; index < list.size(); index++) {
            present |= carried.equals(list.get(index).getAsString());
        }
        if (!present) {
            throw new DejsonException(id + " copies components from " + copyFrom
                    + ", which is not one of its ingredients");
        }

        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "hbm:component_copy_shapeless");
        recipe.addProperty("category", "misc");
        recipe.add("ingredients", list);
        recipe.addProperty("copy_from", carried);
        recipe.add("result", stack(result).json(id));
        return declare(namespace, id, recipe);
    }

    /**
     * A recipe that is only a type.
     *
     * <p>A few recipes are a program rather than an arrangement — what they match and what they
     * produce is decided in Java — and the file exists only to put the serialiser in front of the
     * recipe manager. There is nothing else to declare about them.
     */
    public static JsonObject special(String namespace, String id, String type) {
        Models.requireId(type, "recipe type");
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);
        return declare(namespace, id, recipe);
    }

    /** The rows of a shaped recipe's pattern, top to bottom. */
    public static String[] rows(String... rows) {
        return rows;
    }

    /** One result item. */
    public static Stack stack(String id) {
        return new Stack(id, 1, Map.of());
    }

    /** A result of more than one. */
    public static Stack stack(String id, int count) {
        return new Stack(id, count, Map.of());
    }

    /** An ingredient matched on its components as well as its id. */
    public static Ingredient item(String id) {
        return new Ingredient(id, Map.of(), List.of(), null);
    }

    /** One symbol of a {@link #componentShaped} pattern and the ingredient it stands for. */
    public static Key key(String symbol, Ingredient ingredient) {
        if (ingredient == null) {
            throw new DejsonException("symbol '" + symbol + "' was given no ingredient");
        }
        return new Key(symbol, ingredient, null);
    }

    /** The path a recipe lives at. */
    public static String path(String namespace, String id) {
        Models.requireId(namespace + ":" + id, "recipe");
        return "data/" + namespace + "/recipe/" + id + ".json";
    }

    /**
     * A result item: an id, how many of it, and the components it comes out wearing.
     *
     * <p>A count of one is the default and is not written, so the file says a count only where the
     * count is a decision.
     */
    public static final class Stack {

        private final String id;
        private final int count;
        private final Map<String, Integer> components;

        private Stack(String id, int count, Map<String, Integer> components) {
            this.id = id;
            this.count = count;
            this.components = components;
        }

        /** The same result, wearing one more component. */
        public Stack with(String component, int value) {
            return new Stack(id, count, plus(components, component, value));
        }

        private JsonObject json(String recipe) {
            Models.requireId(id, "result");
            if (count < 1) {
                throw new DejsonException(recipe + " produces " + count + " of " + id);
            }
            JsonObject json = new JsonObject();
            json.addProperty("id", id);
            if (count != 1) {
                json.addProperty("count", count);
            }
            if (!components.isEmpty()) {
                json.add("components", componentMap(components));
            }
            return json;
        }
    }

    /**
     * An ingredient matched on its components: an id, the components the stack has to carry, the
     * ones it has to be without, and what is left behind when it is consumed.
     *
     * <p>The remainder is how a recipe takes the contents of a container without taking the
     * container — a cell of deuterium is spent, and the empty cell stays in the grid.
     */
    public static final class Ingredient {

        private final String id;
        private final Map<String, Integer> components;
        private final List<String> absent;
        private final String remainder;

        private Ingredient(String id, Map<String, Integer> components, List<String> absent,
                           String remainder) {
            this.id = id;
            this.components = components;
            this.absent = absent;
            this.remainder = remainder;
        }

        /** The same ingredient, matching only stacks carrying this component value. */
        public Ingredient with(String component, int value) {
            return new Ingredient(id, plus(components, component, value), absent, remainder);
        }

        /** The same ingredient, matching only stacks carrying none of these components. */
        public Ingredient without(String... components) {
            List<String> absent = new ArrayList<>(this.absent);
            for (String component : components) {
                Models.requireId(component, "component");
                if (absent.contains(component)) {
                    throw new DejsonException(id + " excludes " + component + " twice");
                }
                absent.add(component);
            }
            return new Ingredient(id, this.components, List.copyOf(absent), remainder);
        }

        /** What the grid keeps when this ingredient is consumed. */
        public Ingredient remainder(String id) {
            Models.requireId(id, "remainder");
            return new Ingredient(this.id, components, absent, id);
        }

        private JsonObject json(String recipe) {
            Models.requireId(id, "ingredient");
            for (String component : absent) {
                if (components.containsKey(component)) {
                    throw new DejsonException(recipe + " wants " + id + " both carrying and "
                            + "without " + component);
                }
            }
            JsonArray items = new JsonArray();
            items.add(id);

            JsonObject json = new JsonObject();
            json.add("items", items);
            if (!components.isEmpty()) {
                json.add("components", componentMap(components));
            }
            if (!absent.isEmpty()) {
                JsonArray excluded = new JsonArray();
                absent.forEach(excluded::add);
                json.add("absent_components", excluded);
            }
            if (remainder != null) {
                json.addProperty("remainder", remainder);
            }
            return json;
        }
    }

    /** One symbol of a pattern and the ingredient it stands for. */
    public static final class Key {

        private final String symbol;
        private final Ingredient ingredient;
        private final String plain;

        private Key(String symbol, Ingredient ingredient, String plain) {
            this.symbol = symbol;
            this.ingredient = ingredient;
            this.plain = plain;
        }
    }

    private static JsonObject shapedRecipe(String type, String namespace, String id, Stack result,
                                           String[] pattern, List<Key> keys) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);
        recipe.addProperty("category", "misc");
        recipe.add("key", keyed(id, pattern, keys));
        recipe.add("pattern", patternOf(id, pattern));
        recipe.add("result", result.json(id));
        return declare(namespace, id, recipe);
    }

    private static JsonObject cooking(String type, String id, double experience) {
        if (experience < 0) {
            throw new DejsonException(id + " smelts for " + experience + " experience");
        }
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);
        recipe.addProperty("category", "misc");
        recipe.addProperty("cookingtime", 200);
        recipe.addProperty("experience", experience);
        return recipe;
    }

    /**
     * The pattern, checked against the grid it has to fit in.
     *
     * <p>A ragged pattern is the mistake worth catching here. Vanilla reads the first row's width
     * as the recipe's width and refuses the rest, so a row one character short takes the recipe out
     * of the game without taking it out of the data pack.
     */
    private static JsonArray patternOf(String id, String[] rows) {
        if (rows == null || rows.length == 0) {
            throw new DejsonException(id + " has no pattern");
        }
        if (rows.length > GRID) {
            throw new DejsonException(id + " is " + rows.length + " rows tall");
        }
        JsonArray pattern = new JsonArray();
        for (String row : rows) {
            if (row.length() != rows[0].length()) {
                throw new DejsonException(id + " has a ragged pattern: '" + rows[0]
                        + "' is " + rows[0].length() + " wide and '" + row + "' is " + row.length());
            }
            if (row.isEmpty() || row.length() > GRID) {
                throw new DejsonException(id + " has a row " + row.length() + " wide: '" + row + "'");
            }
            pattern.add(row);
        }
        return pattern;
    }

    /**
     * The symbols, checked against the pattern that uses them.
     *
     * <p>Both directions are mistakes and neither one fails to parse: a symbol the key does not
     * define makes vanilla drop the recipe, and a key nothing in the pattern uses is a symbol
     * somebody mistyped in one place of the two.
     */
    private static JsonObject keyed(String id, String[] pattern, List<Key> keys) {
        JsonObject key = new JsonObject();
        for (Key entry : keys) {
            if (entry.symbol.length() != 1 || entry.symbol.charAt(0) == ' ') {
                throw new DejsonException(id + " has '" + entry.symbol
                        + "' as a pattern symbol; a symbol is one character and not a space");
            }
            if (key.has(entry.symbol)) {
                throw new DejsonException(id + " defines '" + entry.symbol + "' twice");
            }
            if (entry.ingredient != null) {
                key.add(entry.symbol, entry.ingredient.json(id));
            } else {
                key.addProperty(entry.symbol, entry.plain);
            }
        }
        for (String row : pattern) {
            for (int index = 0; index < row.length(); index++) {
                String symbol = row.substring(index, index + 1);
                if (!" ".equals(symbol) && !key.has(symbol)) {
                    throw new DejsonException(id + " uses '" + symbol + "' in its pattern and "
                            + "does not say what it is");
                }
            }
        }
        for (Key entry : keys) {
            boolean used = false;
            for (String row : pattern) {
                used |= row.contains(entry.symbol);
            }
            if (!used) {
                throw new DejsonException(id + " defines '" + entry.symbol
                        + "' and its pattern never uses it");
            }
        }
        return key;
    }

    private static JsonArray ingredientList(String id, String[] ingredients) {
        if (ingredients.length == 0) {
            throw new DejsonException(id + " has no ingredients");
        }
        if (ingredients.length > GRID * GRID) {
            throw new DejsonException(id + " has " + ingredients.length
                    + " ingredients and the grid holds " + GRID * GRID);
        }
        JsonArray list = new JsonArray();
        for (String ingredient : ingredients) {
            list.add(requireIngredient(ingredient));
        }
        return list;
    }

    private static Map<String, Integer> plus(Map<String, Integer> components, String component,
                                             int value) {
        Models.requireId(component, "component");
        if (components.containsKey(component)) {
            throw new DejsonException(component + " is given a value twice");
        }
        Map<String, Integer> plus = new LinkedHashMap<>(components);
        plus.put(component, value);
        return plus;
    }

    private static JsonObject componentMap(Map<String, Integer> components) {
        JsonObject json = new JsonObject();
        components.forEach(json::addProperty);
        return json;
    }

    private static JsonObject declare(String namespace, String id, JsonObject recipe) {
        return Data.declare(path(namespace, id), recipe);
    }

    private static JsonObject result(String id, int count) {
        return stack(id, count).json(id);
    }

    private static String requireIngredient(String ingredient) {
        Models.requireId(ingredient.startsWith("#") ? ingredient.substring(1) : ingredient, "ingredient");
        return ingredient;
    }
}
