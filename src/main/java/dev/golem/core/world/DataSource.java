package dev.golem.core.world;

import com.google.gson.JsonElement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.core.HolderLookup;

/**
 * Files a mod ships in one datapack folder, written in Java and turned into {@code data/<namespace>/<folder>/<id>.json} at
 * build time by {@link DataGenerator}. The rows are the folder's runtime type, and {@link #encode} / {@link #decode} are
 * the reader's own codec (or its own reader/writer pair), so the file is exactly what the game reads and what a datapack
 * would write. Every row is decoded and encoded again before it is written; a row the format cannot state fails the run.
 * A mod keeps its sources in a source set it does not ship.
 *
 * <p>{@link RecipeSource} is the source of one recipe table; other folders (fluid properties, data maps, item pools, a
 * mod's {@code recipe_removals}, ...) subclass this directly.
 *
 * @param <R> the row type
 */
public abstract class DataSource<R> {

    private static final Pattern ID = Pattern.compile("[a-z0-9_.\\-/]+");

    private final String folder;
    private final Map<String, R> rows = new LinkedHashMap<>();
    private boolean registered;

    /** {@code folder}: the path under {@code data/<namespace>/}, e.g. {@code fluid_property} or {@code value_maps/item}. */
    protected DataSource(String folder) {
        if (!ID.matcher(folder).matches()) throw new IllegalArgumentException("not a data folder: " + folder);
        this.folder = folder;
    }

    /** The folder under {@code data/<namespace>/} the rows are written to. */
    public final String folder() {
        return folder;
    }

    /** Adds every row with {@link #register}; called once, on a started server (registries and components exist). */
    protected abstract void registerDefaults();

    /** The file content of a row. */
    public abstract JsonElement encode(R row, HolderLookup.Provider registries);

    /** A file read back, for the round-trip check before writing. */
    public abstract R decode(JsonElement json, HolderLookup.Provider registries);

    /** The position the next row takes (registration order). */
    protected final int nextOrder() {
        return rows.size();
    }

    /** Adds a row as {@code data/<namespace>/<folder>/<id>.json}. */
    protected void register(String id, R row) {
        add(id, row);
    }

    /** {@link #register} without what a subclass adds to it. */
    protected final void add(String id, R row) {
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException(folder + ": not a file id: " + id);
        if (rows.putIfAbsent(id, row) != null) throw new IllegalStateException(folder + ": two rows with the id " + id);
    }

    /** Every row with its id, in registration order. */
    public final synchronized Map<String, R> rows() {
        if (!registered) {
            registerDefaults();
            registered = true;
        }
        return Collections.unmodifiableMap(rows);
    }

    /** A short name for logs and errors. */
    public String describe() {
        return folder;
    }
}
