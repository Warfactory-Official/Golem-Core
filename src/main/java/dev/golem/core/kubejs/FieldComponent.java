package dev.golem.core.kubejs;

//? if kubejs && >=1.20.5 {

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.golem.core.Ids;
import dev.golem.core.world.RecipeFields;
import dev.golem.core.world.RecipeFields.Field;
import dev.golem.core.world.RecipeFields.FluidAmount;
import dev.golem.core.world.RecipeFields.ItemIn;
import dev.golem.core.world.RecipeFields.ItemOut;
import dev.golem.core.world.RecipeTables;
import dev.latvian.mods.kubejs.fluid.FluidWrapper;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.IngredientWrapper;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.ItemWrapper;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.SizedIngredientWrapper;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.FluidMatch;
import dev.latvian.mods.kubejs.recipe.match.ItemMatch;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * One {@link RecipeFields.Field} of a recipe table as a KubeJS recipe component. Its value is the field's row JSON,
 * read from and written to the recipe unchanged, so the table's codec stays the only parser. A script value becomes that
 * JSON here: JSON and plain JS objects pass through as they are; anything else is read the KubeJS way for the field's
 * kind (an item or tag with a count, an item stack, a fluid stack, a number, a string) into a neutral value and
 * written by the field's {@link RecipeFields.Shape} ({@link FieldJson}). Filters and replacements by item or fluid
 * read the row back through the same shape.
 */
final class FieldComponent implements RecipeComponent<JsonElement> {

    /** The component's own JSON form (KubeJS's schema export): the table and field it describes. */
    static final MapCodec<FieldComponent> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("table").forGetter(component -> component.table),
            Codec.STRING.fieldOf("field").forGetter(component -> component.field.name())
    ).apply(instance, FieldComponent::lookup));

    // KubeJS 8 names a component type by registry key (registered with its codec); KubeJS 7 by a type object.
    //? if >=1.21.2 {
    static final ResourceKey<RecipeComponentType<?>> TYPE = RecipeComponentType.key(Ids.of("golemcore", "table_field"));
    //?} else {
    /*static final RecipeComponentType<?> TYPE = RecipeComponentType.dynamic(Ids.of("golemcore", "table_field"), MAP_CODEC);
    *///?}

    private final String table;
    private final Field field;

    FieldComponent(String table, Field field) {
        this.table = table;
        this.field = field;
    }

    private static FieldComponent lookup(String table, String field) {
        for (RecipeTables.Table<?> each : RecipeTables.all()) {
            if (!each.id().equals(table)) continue;
            Field found = each.fields().get(field);
            if (found != null) return new FieldComponent(table, found);
        }
        throw new IllegalArgumentException("No recipe table field " + table + "." + field);
    }

    @Override
    //? if >=1.21.2 {
    public ResourceKey<RecipeComponentType<?>> type() {
    //?} else {
    /*public RecipeComponentType<?> type() {
    *///?}
        return TYPE;
    }

    @Override
    public Codec<JsonElement> codec() {
        return ExtraCodecs.JSON;
    }

    @Override
    public TypeInfo typeInfo() {
        TypeInfo one = switch (field.kind()) {
            case ITEM_IN -> SizedIngredientWrapper.TYPE_INFO;
            case ITEM_OUT -> ItemWrapper.TYPE_INFO;
            case FLUID_IN, FLUID_OUT -> FluidWrapper.TYPE_INFO;
            case INT -> TypeInfo.INT;
            case LONG -> TypeInfo.LONG;
            case DOUBLE -> TypeInfo.DOUBLE;
            case BOOLEAN -> TypeInfo.BOOLEAN;
            case STRING, ID -> TypeInfo.STRING;
            case JSON -> TypeInfo.OBJECT;
        };
        return field.isList() ? one.asArray() : one;
    }

    // ----- script value -> row JSON -------------------------------------------------------------------------------

    @Override
    public JsonElement wrap(RecipeScriptContext cx, @Nullable Object from) {
        from = Wrapper.unwrapped(from);
        if (from == null) return null;
        if (from instanceof JsonElement json) return json;
        if (field.isList()) {
            JsonArray array = new JsonArray();
            if (from instanceof Iterable<?> values && !(from instanceof Map<?, ?>)) {
                for (Object value : values) array.add(one(cx, value));
            } else if (from instanceof Object[] values) {
                for (Object value : values) array.add(one(cx, value));
            } else {
                array.add(one(cx, from));
            }
            return array;
        }
        return one(cx, from);
    }

    private JsonElement one(RecipeScriptContext cx, Object from) {
        try {
            return convert(cx, from);
        } catch (IllegalArgumentException e) {
            // A shape refusing a value (e.g. a chance on an output that has none) is the script's error: name the field.
            throw new IllegalArgumentException(describe() + ": " + e.getMessage(), e);
        }
    }

    /** One element (a list field's shape applies per element, never to the whole array). */
    private JsonElement convert(RecipeScriptContext cx, Object from) {
        from = Wrapper.unwrapped(from);
        Context context = cx.cx();
        if (from instanceof JsonElement json) return json;
        // A plain object is the row's own JSON for this field (a table's special forms stay writable).
        if (field.kind() == RecipeFields.Kind.JSON || from instanceof Map<?, ?>) return JsonUtils.of(context, from);
        return switch (field.kind()) {
            case ITEM_IN -> itemIn(cx, from);
            case ITEM_OUT -> {
                ItemStack stack = ItemWrapper.wrap(context, from);
                if (stack.isEmpty()) throw new IllegalArgumentException("empty item: " + from);
                yield FieldJson.write(field, new ItemOut(itemId(stack.getItem()), stack.getCount(), null));
            }
            case FLUID_IN, FLUID_OUT -> {
                FluidStack stack = FluidWrapper.wrap(context, from);
                if (stack.isEmpty()) throw new IllegalArgumentException("empty fluid: " + from);
                yield FieldJson.write(field, new FluidAmount(
                        BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString(), stack.getAmount()));
            }
            case INT -> FieldJson.write(field, ((Number) context.jsToJava(from, TypeInfo.NUMBER)).intValue());
            case LONG -> FieldJson.write(field, ((Number) context.jsToJava(from, TypeInfo.NUMBER)).longValue());
            case DOUBLE -> FieldJson.write(field, ((Number) context.jsToJava(from, TypeInfo.NUMBER)).doubleValue());
            case BOOLEAN -> FieldJson.write(field, context.jsToJava(from, TypeInfo.BOOLEAN));
            case STRING, ID -> FieldJson.write(field, from.toString());
            case JSON -> JsonUtils.of(context, from);
        };
    }

    private JsonElement itemIn(RecipeScriptContext cx, Object from) {
        var sized = SizedIngredientWrapper.wrap(cx.cx(), from);
        Ingredient ingredient = sized.ingredient();
        if (ingredient.isEmpty()) throw new IllegalArgumentException("empty ingredient: " + from);
        var tag = IngredientWrapper.tagKeyOf(ingredient);
        if (tag != null) return FieldJson.write(field, new ItemIn(tag.location().toString(), true, sized.count()));
        List<Item> items = items(ingredient);
        if (items.size() == 1) return FieldJson.write(field, new ItemIn(itemId(items.get(0)), false, sized.count()));
        if (field.shape() != null) {
            throw new IllegalArgumentException("takes one item or one tag, not " + from);
        }
        // No shape of its own: the default shape is vanilla's, which can hold any ingredient.
        JsonElement any = Ingredient.CODEC.encodeStart(cx.ops().json(), ingredient).getOrThrow();
        if (sized.count() == 1) return any;
        var counted = new com.google.gson.JsonObject();
        counted.add("ingredient", any);
        counted.addProperty("count", sized.count());
        return counted;
    }

    /** The items a plain (non-tag) ingredient lists. */
    @SuppressWarnings("deprecation") // Ingredient.items(): 26.1 keeps it for exactly this, listing a plain ingredient
    private static List<Item> items(Ingredient ingredient) {
        //? if >=1.21.2 {
        return ingredient.items().map(Holder::value).distinct().toList();
        //?} else {
        /*return java.util.Arrays.stream(ingredient.getItems()).map(ItemStack::getItem).distinct().toList();
        *///?}
    }

    private String describe() {
        return table + "." + field.name();
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    // ----- filters and replacements -------------------------------------------------------------------------------

    @Override
    public boolean matches(RecipeMatchContext cx, JsonElement value, ReplacementMatchInfo match) {
        for (JsonElement element : FieldJson.elements(field, value)) {
            if (matchesOne(cx, element, match)) return true;
        }
        return false;
    }

    private boolean matchesOne(RecipeMatchContext cx, JsonElement element, ReplacementMatchInfo match) {
        Object neutral = FieldJson.read(field, element);
        if (neutral instanceof ItemIn in && match.match() instanceof ItemMatch items) {
            Ingredient ingredient = Ingredient.CODEC.parse(cx.ops().json(), FieldJson.vanillaIngredient(in)).result().orElse(null);
            return ingredient != null && !ingredient.isEmpty() && items.matches(cx, ingredient, match.exact());
        }
        if (neutral instanceof ItemOut out && match.match() instanceof ItemMatch items) {
            ItemStack stack = stack(out.id(), out.count());
            return !stack.isEmpty() && items.matches(cx, stack, match.exact());
        }
        if (neutral instanceof FluidAmount fluid && match.match() instanceof FluidMatch fluids) {
            FluidStack stack = BuiltInRegistries.FLUID.getOptional(Ids.parse(fluid.id()))
                    .map(f -> new FluidStack(f, fluid.amount())).orElse(FluidStack.EMPTY);
            return !stack.isEmpty() && fluids.matches(cx, stack, match.exact());
        }
        return false;
    }

    @Override
    public JsonElement replace(RecipeScriptContext cx, JsonElement original, ReplacementMatchInfo match, Object with) {
        if (field.isList() && original instanceof JsonArray array) {
            JsonArray replaced = new JsonArray();
            boolean changed = false;
            for (JsonElement element : array) {
                JsonElement next = replaceOne(cx, element, match, with);
                changed |= next != element;
                replaced.add(next);
            }
            return changed ? replaced : original;
        }
        return replaceOne(cx, original, match, with);
    }

    private JsonElement replaceOne(RecipeScriptContext cx, JsonElement element, ReplacementMatchInfo match, Object with) {
        if (!matchesOne(cx, element, match)) return element;
        JsonElement next = one(cx, with);
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

    private static ItemStack stack(String id, int count) {
        return BuiltInRegistries.ITEM.getOptional(Ids.parse(id)).map(item -> new ItemStack(item, Math.max(1, count)))
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public boolean isEmpty(JsonElement value) {
        return value == null || value.isJsonNull() || (value instanceof JsonArray array && array.isEmpty() && !field.isOptional());
    }

    @Override
    public boolean allowEmpty() {
        return field.isOptional();
    }

    @Override
    public String toString() {
        return "golemcore:table_field[" + describe() + "]";
    }
}
//?}
