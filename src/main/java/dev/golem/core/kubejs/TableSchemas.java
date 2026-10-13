package dev.golem.core.kubejs;

//? if kubejs && >=1.20.5 {

import com.google.gson.JsonElement;
import dev.golem.core.Ids;
import dev.golem.core.world.RecipeFields;
import dev.golem.core.world.RecipeFields.Field;
import dev.golem.core.world.RecipeTables;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.ComponentRole;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every {@link RecipeTables} table as a KubeJS recipe schema under the table's id, so scripts write
 * {@code event.recipes.<ns>.<table>(required..., optional...)} with the table's {@link RecipeTables.Table#fields()} as
 * positional arguments (required ones first, each group in stated order) and one builder method per field and alias
 * ({@code .duration(200)}). Fields with an input or output role take part in {@code event.remove({input/output: ...})}
 * and {@code replaceInput}/{@code replaceOutput}. A single plain object, where no one-argument form exists, is the whole
 * row ({@code event.recipes.hbm.press({...})}); {@code event.custom({...})} always works.
 */
final class TableSchemas {

    private static final Logger LOGGER = LoggerFactory.getLogger("Golem Core scripts");

    private TableSchemas() {}

    static void register(RecipeSchemaRegistry registry) {
        int count = 0;
        for (RecipeTables.Table<?> table : RecipeTables.all()) {
            try {
                registry.register(Ids.parse(table.id()), schema(table));
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
        RecipeKey<JsonElement> key = new FieldComponent(table, field).key(field.name(), role(field.role()));
        List<String> functions = new ArrayList<>(1 + field.aliases().size());
        functions.add(field.name());
        functions.addAll(field.aliases());
        key.functionNames(functions);
        if (field.isOptional()) {
            if (field.fallback() != null) {
                key.optional(field.fallback()).alwaysWrite();
            } else {
                key.defaultOptional();
            }
        }
        return key;
    }

    private static ComponentRole role(RecipeFields.Role role) {
        return switch (role) {
            case INPUT -> ComponentRole.INPUT;
            case OUTPUT -> ComponentRole.OUTPUT;
            case OTHER -> ComponentRole.OTHER;
        };
    }
}
//?}
