package dev.golem.core.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;

/**
 * {@code recipe_removals} lists ({@link RecipeTables#REMOVALS}) for recipes that are not a {@link RecipeSource}'s table
 * (another mod's crafting or smelting rows): each row is one file, {@code {"recipes": [ids]}}. Removals of table rows
 * are stated by the table's source itself ({@link RecipeSource#remove}).
 */
public abstract class RemovalSource extends DataSource<List<String>> {

    protected RemovalSource() {
        super(RecipeTables.REMOVALS);
    }

    @Override
    public JsonElement encode(List<String> ids, HolderLookup.Provider registries) {
        JsonArray recipes = new JsonArray();
        ids.stream().sorted().forEach(recipes::add);
        JsonObject file = new JsonObject();
        file.add("recipes", recipes);
        return file;
    }

    @Override
    public List<String> decode(JsonElement json, HolderLookup.Provider registries) {
        List<String> ids = new ArrayList<>();
        for (JsonElement id : json.getAsJsonObject().getAsJsonArray("recipes")) ids.add(id.getAsString());
        return ids;
    }
}
