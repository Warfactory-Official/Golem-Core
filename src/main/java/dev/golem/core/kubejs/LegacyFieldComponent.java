package dev.golem.core.kubejs;

//? if kubejs && <1.20.5 {

/*import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.golem.core.Ids;
import dev.golem.core.world.RecipeFields;
import dev.golem.core.world.RecipeFields.Field;
import dev.golem.core.world.RecipeFields.FluidAmount;
import dev.golem.core.world.RecipeFields.ItemIn;
import dev.golem.core.world.RecipeFields.ItemOut;
import dev.latvian.mods.kubejs.fluid.FluidLike;
import dev.latvian.mods.kubejs.fluid.FluidStackJS;
import dev.latvian.mods.kubejs.item.InputItem;
import dev.latvian.mods.kubejs.item.OutputItem;
import dev.latvian.mods.kubejs.item.ingredient.IngredientJS;
import dev.latvian.mods.kubejs.recipe.InputReplacement;
import dev.latvian.mods.kubejs.recipe.ItemMatch;
import dev.latvian.mods.kubejs.recipe.OutputReplacement;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.ReplacementMatch;
import dev.latvian.mods.kubejs.recipe.component.ComponentRole;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.util.JsonIO;
import dev.latvian.mods.rhino.Wrapper;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/^*
 * KubeJS 6's spelling of {@link FieldComponent}: one {@link RecipeFields.Field} of a recipe table as a recipe component
 * whose value is the field's row JSON. KubeJS 6 has no codecs here; a component reads from either the recipe file's
 * JSON (kept unchanged) or a script value, and states its role itself. Script values become neutral values the KubeJS 6
 * way ({@link InputItem}, {@link OutputItem}, {@link FluidStackJS}) and are written by the field's shape
 * ({@link FieldJson}), so a script line reads the same as on KubeJS 7/8.
 *
 * <p>Fluid amounts: KubeJS 6 counts in its loader's units (Fabric: droplets, 81,000 a bucket; Forge: mB); a neutral
 * {@link FluidAmount} is always mB, so the amount is scaled by the platform's bucket ({@code FluidStack.bucketAmount()}).
 ^/
final class LegacyFieldComponent implements RecipeComponent<JsonElement> {

    private final String table;
    private final Field field;

    LegacyFieldComponent(String table, Field field) {
        this.table = table;
        this.field = field;
    }

    @Override
    public ComponentRole role() {
        switch (field.role()) {
            case INPUT:
                return ComponentRole.INPUT;
            case OUTPUT:
                return ComponentRole.OUTPUT;
            default:
                return ComponentRole.OTHER;
        }
    }

    @Override
    public String componentType() {
        return "golemcore:table_field";
    }

    @Override
    public Class<?> componentClass() {
        return JsonElement.class;
    }

    @Override
    public JsonElement write(RecipeJS recipe, JsonElement value) {
        return value;
    }

    // ----- script value or file JSON -> row JSON ------------------------------------------------------------------

    @Override
    public JsonElement read(RecipeJS recipe, Object from) {
        from = Wrapper.unwrapped(from);
        if (from == null) return null;
        if (from instanceof JsonElement json) return json; // the recipe file's own JSON, or a script's JSON
        if (field.isList()) {
            JsonArray array = new JsonArray();
            if (from instanceof Iterable<?> values && !(from instanceof Map<?, ?>)) {
                for (Object value : values) array.add(one(value));
            } else if (from instanceof Object[] values) {
                for (Object value : values) array.add(one(value));
            } else {
                array.add(one(from));
            }
            return array;
        }
        return one(from);
    }

    private JsonElement one(Object from) {
        try {
            return convert(Wrapper.unwrapped(from));
        } catch (IllegalArgumentException e) {
            // A shape refusing a value (e.g. a chance on an output that has none) is the script's error: name the field.
            throw new IllegalArgumentException(describe() + ": " + e.getMessage(), e);
        }
    }

    private JsonElement convert(Object from) {
        if (from instanceof JsonElement json) return json;
        // A plain object is the row's own JSON for this field (a table's special forms stay writable).
        if (field.kind() == RecipeFields.Kind.JSON || from instanceof Map<?, ?>) return JsonIO.of(from);
        switch (field.kind()) {
            case ITEM_IN:
                return itemIn(from);
            case ITEM_OUT: {
                OutputItem out = OutputItem.of(from);
                if (out.isEmpty()) throw new IllegalArgumentException("empty item: " + from);
                Float chance = out.hasChance() ? (float) out.getChance() : null;
                return FieldJson.write(field, new ItemOut(itemId(out.item), out.getCount(), chance));
            }
            case FLUID_IN:
            case FLUID_OUT: {
                FluidStackJS fluid = FluidStackJS.of(from);
                if (fluid.kjs$isEmpty()) throw new IllegalArgumentException("empty fluid: " + from);
                return FieldJson.write(field, new FluidAmount(fluid.getId(), toMb(fluid.getAmount())));
            }
            case INT:
                return FieldJson.write(field, ((Number) from).intValue());
            case LONG:
                return FieldJson.write(field, ((Number) from).longValue());
            case DOUBLE:
                return FieldJson.write(field, ((Number) from).doubleValue());
            case BOOLEAN:
                return FieldJson.write(field, from instanceof Boolean b ? b : Boolean.parseBoolean(from.toString()));
            default:
                return FieldJson.write(field, from.toString());
        }
    }

    private JsonElement itemIn(Object from) {
        InputItem in = InputItem.of(from);
        if (in.isEmpty()) throw new IllegalArgumentException("empty ingredient: " + from);
        // Vanilla 1.20.1 writes a one-item or one-tag ingredient as {"item"} / {"tag"}: read it back as a neutral value.
        JsonElement vanilla = in.ingredient.toJson();
        ItemIn single = vanilla.isJsonObject() ? FieldJson.readItemIn(vanilla) : null;
        if (single != null) return FieldJson.write(field, new ItemIn(single.id(), single.tag(), in.count));
        if (field.shape() != null) throw new IllegalArgumentException("takes one item or one tag, not " + from);
        // No shape of its own: the default shape is vanilla's, which can hold any ingredient.
        if (in.count == 1) return vanilla;
        JsonObject counted = new JsonObject();
        counted.add("ingredient", vanilla);
        counted.addProperty("count", in.count);
        return counted;
    }

    /^* A KubeJS 6 fluid amount (the platform's units) in mB. ^/
    private static int toMb(long amount) {
        long bucket = dev.architectury.fluid.FluidStack.bucketAmount();
        return (int) Math.min(Integer.MAX_VALUE, amount * 1000L / bucket);
    }

    private static long fromMb(int mb) {
        return mb * dev.architectury.fluid.FluidStack.bucketAmount() / 1000L;
    }

    private String describe() {
        return table + "." + field.name();
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // ----- filters and replacements -------------------------------------------------------------------------------

    @Override
    public boolean isInput(RecipeJS recipe, JsonElement value, ReplacementMatch match) {
        return field.role() == RecipeFields.Role.INPUT && matches(value, match);
    }

    @Override
    public boolean isOutput(RecipeJS recipe, JsonElement value, ReplacementMatch match) {
        return field.role() == RecipeFields.Role.OUTPUT && matches(value, match);
    }

    private boolean matches(JsonElement value, ReplacementMatch match) {
        for (JsonElement element : FieldJson.elements(field, value)) {
            if (matchesOne(element, match)) return true;
        }
        return false;
    }

    private boolean matchesOne(JsonElement element, ReplacementMatch match) {
        Object neutral = FieldJson.read(field, element);
        if (neutral instanceof ItemIn in && match instanceof ItemMatch items) {
            Ingredient ingredient = IngredientJS.of(in.tag() ? "#" + in.id() : in.id());
            return ingredient != Ingredient.EMPTY && items.contains(ingredient);
        }
        if (neutral instanceof ItemOut out && match instanceof ItemMatch items) {
            ItemStack stack = BuiltInRegistries.ITEM.getOptional(Ids.parse(out.id()))
                    .map(item -> new ItemStack(item, Math.max(1, out.count()))).orElse(ItemStack.EMPTY);
            return !stack.isEmpty() && items.contains(stack);
        }
        if (neutral instanceof FluidAmount fluid && match instanceof FluidLike fluids) {
            FluidStackJS stack = FluidStackJS.of(fluid.id(), fromMb(fluid.amount()), null);
            return !stack.kjs$isEmpty() && stack.matches(fluids);
        }
        return false;
    }

    @Override
    public JsonElement replaceInput(RecipeJS recipe, JsonElement original, ReplacementMatch match, InputReplacement with) {
        return field.role() == RecipeFields.Role.INPUT ? replace(original, match, with) : original;
    }

    @Override
    public JsonElement replaceOutput(RecipeJS recipe, JsonElement original, ReplacementMatch match, OutputReplacement with) {
        return field.role() == RecipeFields.Role.OUTPUT ? replace(original, match, with) : original;
    }

    private JsonElement replace(JsonElement original, ReplacementMatch match, Object with) {
        if (field.isList() && original instanceof JsonArray array) {
            JsonArray replaced = new JsonArray();
            boolean changed = false;
            for (JsonElement element : array) {
                JsonElement next = replaceOne(element, match, with);
                changed |= next != element;
                replaced.add(next);
            }
            return changed ? replaced : original;
        }
        return replaceOne(original, match, with);
    }

    private JsonElement replaceOne(JsonElement element, ReplacementMatch match, Object with) {
        if (!matchesOne(element, match)) return element;
        JsonElement next = one(with);
        // The replacement names what, the row keeps how many: a counted input or output keeps its count (and chance).
        Object was = FieldJson.read(field, element);
        Object now = FieldJson.read(field, next);
        if (was instanceof ItemIn in && now instanceof ItemIn to && to.count() == 1 && in.count() != 1) {
            return FieldJson.write(field, new ItemIn(to.id(), to.tag(), in.count()));
        }
        if (was instanceof ItemOut out && now instanceof ItemOut to && to.count() == 1) {
            return FieldJson.write(field, new ItemOut(to.id(), out.count(), to.chance() != null ? to.chance() : out.chance()));
        }
        return next;
    }

    @Override
    public boolean checkValueHasChanged(JsonElement oldValue, JsonElement newValue) {
        return oldValue != newValue;
    }

    @Override
    public String toString() {
        return "golemcore:table_field[" + describe() + "]";
    }
}
*///?}
