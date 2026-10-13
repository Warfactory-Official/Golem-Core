package dev.golem.core.kubejs;

//? if kubejs && <1.20.5 {

/*import com.google.gson.JsonElement;
import dev.golem.core.Ids;
import dev.golem.core.world.RecipeFields.Field;
import dev.golem.core.world.RecipeTables;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RegisterRecipeSchemasEvent;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/^*
 * KubeJS 6's spelling of {@link TableSchemas}: the same schema per table (required fields first, then optional ones,
 * each in stated order) built from {@link LegacyFieldComponent}s. KubeJS 6 names a builder method after each of a
 * key's names, so a field's aliases are its alternative names ({@code .inputItems(...)} and {@code .input_items(...)}).
 ^/
final class LegacyTableSchemas {

    private static final Logger LOGGER = LoggerFactory.getLogger("Golem Core scripts");

    private LegacyTableSchemas() {}

    static void register(RegisterRecipeSchemasEvent event) {
        int count = 0;
        for (RecipeTables.Table<?> table : RecipeTables.all()) {
            try {
                event.register(Ids.parse(table.id()), schema(table));
                count++;
            } catch (RuntimeException e) {
                LOGGER.error("No KubeJS recipe schema for table {}", table.id(), e);
            }
        }
        LOGGER.info("Registered {} recipe table schema(s) with KubeJS", count);
    }

    static RecipeSchema schema(RecipeTables.Table<?> table) {
        List<Field> ordered = new ArrayList<>();
        for (Field field : table.fields().fields()) if (!field.isOptional()) ordered.add(field);
        for (Field field : table.fields().fields()) if (field.isOptional()) ordered.add(field);
        List<RecipeKey<?>> keys = new ArrayList<>(ordered.size());
        for (Field field : ordered) keys.add(key(table.id(), field));
        return new RecipeSchema(keys.toArray(RecipeKey[]::new));
    }

    private static RecipeKey<JsonElement> key(String table, Field field) {
        RecipeKey<JsonElement> key = new LegacyFieldComponent(table, field).key(field.name());
        for (String alias : field.aliases()) {
            if (!alias.equals(field.name())) key.alt(alias);
        }
        key.preferred(field.name());
        if (field.isOptional()) {
            key.allowEmpty();
            if (field.fallback() != null) {
                key.optional(field.fallback()).alwaysWrite();
            } else {
                key.defaultOptional();
            }
        }
        return key;
    }
}
*///?}
