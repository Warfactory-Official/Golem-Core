package dev.golem.core.world;

import java.util.List;

/**
 * One mod's {@link DataSource}s ({@link RecipeSource} tables and other data folders). The generating run names the class
 * ({@code -Dgolem.data.sources}, see {@link DataGenerator}) and writes every row into
 * {@code data/<namespace>/<folder>/}. A mod's sources may fill another mod's tables (an addon's rows for its base mod's
 * machines): the files are then in the namespace given here and carry the other table's {@code "type"}.
 */
public interface DataSources {

    /** The namespace the files are written in: the mod that ships them. */
    String namespace();

    /** The sources: one per recipe table, and any other {@link DataSource}s (fluid properties, data maps, ...). */
    List<? extends DataSource<?>> sources();
}
