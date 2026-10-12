package dev.golem.core.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.HolderLookup;

/**
 * The rows a mod ships for one {@link RecipeTables} table, written in Java and turned into datapack files at build time
 * by {@link DataGenerator}: a {@link DataSource} for {@code recipe/<table>}, whose files carry the table's
 * {@code "type"}. The rows are the table's own runtime row type and are written with the table's codec
 * ({@link RecipeTables.Table#toJson}), so there is one schema.
 *
 * <p>A source may also remove rows another mod ships ({@link #remove}): those ids are written to the source's own
 * {@code recipe_removals/<table>.json}, so a row that replaces another mod's and its removal are stated together.
 *
 * @param <R> the table's row type
 */
public abstract class RecipeSource<R> extends DataSource<R> {

    private final RecipeTables.Table<R> table;
    private final Set<String> removals = new TreeSet<>();

    protected RecipeSource(RecipeTables.Table<R> table) {
        super("recipe/" + table.id().substring(table.id().indexOf(':') + 1));
        this.table = table;
    }

    public final RecipeTables.Table<R> table() {
        return table;
    }

    @Override
    public JsonElement encode(R row, HolderLookup.Provider registries) {
        JsonObject file = new JsonObject();
        file.addProperty("type", table.id());
        for (Map.Entry<String, JsonElement> field : table.toJson(row, registries).getAsJsonObject().entrySet()) {
            file.add(field.getKey(), field.getValue());
        }
        return file;
    }

    @Override
    public R decode(JsonElement json, HolderLookup.Provider registries) {
        JsonObject body = json.getAsJsonObject().deepCopy();
        body.remove("type");
        return table.fromJson(body, registries);
    }

    /**
     * Takes another mod's row of this table out of the game ({@code <namespace>:<table>/<id>}): it is listed in this
     * source's {@code recipe_removals}, which Golem Core applies to every pack.
     */
    protected final void remove(String recipeId) {
        if (!removals.add(recipeId)) throw new IllegalStateException(table.id() + ": " + recipeId + " removed twice");
    }

    /** The recipe ids this source removes (after {@link #rows()}). */
    public final Set<String> removals() {
        rows();
        return java.util.Collections.unmodifiableSet(removals);
    }

    /**
     * A row of this table as the generating server loaded it ({@code <namespace>:<table>/<id>}, e.g.
     * {@code hbm:press/plate_titanium}), read from the datapack files and before any {@code recipe_removals}: how an
     * addon places its rows by its base mod's (an {@code order} taken from the row it follows or replaces, so it stays
     * right when the base mod reorders). Only while the generator runs; fails if the row does not exist.
     */
    protected final R loaded(String recipeId) {
        return DataGenerator.loaded(table, recipeId);
    }

    @Override
    public String describe() {
        return table.id();
    }
}
