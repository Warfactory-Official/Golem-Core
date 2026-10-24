package dev.golem.core.kubejs;

//? if kubejs {

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.golem.core.world.ItemPatch;
import dev.golem.core.world.RecipeFields;
import dev.golem.core.world.RecipeFields.Field;
import dev.golem.core.world.RecipeFields.FluidAmount;
import dev.golem.core.world.RecipeFields.ItemIn;
import dev.golem.core.world.RecipeFields.ItemOut;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * A field's JSON, between the neutral values of {@link RecipeFields} and the row: the field's own
 * {@link RecipeFields.Shape} when it states one, otherwise the kind's default shape, which is this node's vanilla
 * spelling (an ingredient is {@code "id"} / {@code "#tag"} from 1.21.2 and {@code {"item"}} / {@code {"tag"}} below; an
 * item stack is {@code {"id", "count"}} from 1.20.5 and {@code {"item", "count"}} below, with its data under
 * {@link ItemPatch#KEY}). The default for a counted
 * input is {@code {"ingredient": <ingredient>, "count": n}}; a fluid is {@code {"fluid": id, "amount": mB}}.
 *
 * <p>No KubeJS types: every API line's adapter shares this.
 */
final class FieldJson {

    private FieldJson() {}

    /** One value (never a list) as the field's JSON. */
    static JsonElement write(Field field, Object value) {
        if (value instanceof JsonElement json) return json;
        if (field.shape() != null) return field.shape().write(value);
        return switch (field.kind()) {
            case ITEM_IN -> value instanceof ItemIn in ? itemIn(in) : primitive(value);
            case ITEM_OUT -> value instanceof ItemOut out ? itemOut(out) : primitive(value);
            case FLUID_IN, FLUID_OUT -> value instanceof FluidAmount fluid ? fluid(fluid) : primitive(value);
            default -> primitive(value);
        };
    }

    /** The neutral value of one JSON element of the field (an element of a list field), or null when unknown. */
    @Nullable
    static Object read(Field field, JsonElement json) {
        try {
            if (field.shape() != null) return field.shape().read(json);
            return switch (field.kind()) {
                case ITEM_IN -> readItemIn(json);
                case ITEM_OUT -> readItemOut(json);
                case FLUID_IN, FLUID_OUT -> readFluid(json);
                default -> null;
            };
        } catch (RuntimeException e) {
            return null; // a hand-written row the shape cannot read: not filterable, still written unchanged
        }
    }

    /** The elements of a field's value: the array's items for a list field, else the value itself. */
    static List<JsonElement> elements(Field field, JsonElement value) {
        if (field.isList() && value instanceof JsonArray array) {
            List<JsonElement> all = new ArrayList<>(array.size());
            array.forEach(all::add);
            return all;
        }
        return List.of(value);
    }

    static JsonElement primitive(Object value) {
        // No pattern switch: the 1.20.1 nodes compile this on Java 17.
        if (value == null) return com.google.gson.JsonNull.INSTANCE;
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Number n) return new JsonPrimitive(n);
        if (value instanceof Character c) return new JsonPrimitive(c);
        return new JsonPrimitive(value.toString());
    }

    // Default shapes

    static JsonElement itemIn(ItemIn in) {
        JsonElement ingredient = vanillaIngredient(in);
        if (in.count() == 1) return ingredient;
        JsonObject counted = new JsonObject();
        counted.add("ingredient", ingredient);
        counted.addProperty("count", in.count());
        return counted;
    }

    static JsonElement itemOut(ItemOut out) {
        JsonObject json = vanillaStack(out.id(), out.count());
        if (out.data() != null) json.add(ItemPatch.KEY, out.data());
        if (out.chance() != null) json.addProperty("chance", out.chance());
        return json;
    }

    static JsonElement fluid(FluidAmount fluid) {
        JsonObject json = new JsonObject();
        json.addProperty("fluid", fluid.id());
        json.addProperty("amount", fluid.amount());
        return json;
    }

    @Nullable
    static ItemIn readItemIn(JsonElement json) {
        if (json.isJsonPrimitive()) {
            String text = json.getAsString();
            return text.startsWith("#") ? new ItemIn(text.substring(1), true, 1) : new ItemIn(text, false, 1);
        }
        if (!json.isJsonObject()) return null;
        JsonObject object = json.getAsJsonObject();
        int count = object.has("count") ? object.get("count").getAsInt() : 1;
        if (object.has("ingredient")) {
            ItemIn inner = readItemIn(object.get("ingredient"));
            return inner == null ? null : new ItemIn(inner.id(), inner.tag(), count);
        }
        if (object.has("tag")) return new ItemIn(object.get("tag").getAsString(), true, count);
        if (object.has("item")) return new ItemIn(object.get("item").getAsString(), false, count);
        if (object.has("id")) return new ItemIn(object.get("id").getAsString(), false, count);
        return null;
    }

    @Nullable
    static ItemOut readItemOut(JsonElement json) {
        if (json.isJsonPrimitive()) return new ItemOut(json.getAsString(), 1, null);
        if (!json.isJsonObject()) return null;
        JsonObject object = json.getAsJsonObject();
        String id = object.has("id") ? object.get("id").getAsString()
                : object.has("item") ? object.get("item").getAsString() : null;
        if (id == null) return null;
        int count = object.has("count") ? object.get("count").getAsInt() : 1;
        Float chance = object.has("chance") ? object.get("chance").getAsFloat() : null;
        return new ItemOut(id, count, chance, object.get(ItemPatch.KEY));
    }

    @Nullable
    static FluidAmount readFluid(JsonElement json) {
        if (!json.isJsonObject()) return null;
        JsonObject object = json.getAsJsonObject();
        String id = object.has("fluid") ? object.get("fluid").getAsString()
                : object.has("id") ? object.get("id").getAsString() : null;
        if (id == null) return null;
        int amount = object.has("amount") ? object.get("amount").getAsInt() : 1000;
        return new FluidAmount(id, amount);
    }

    // This node's vanilla spellings

    /** A one-item or one-tag ingredient as this node's vanilla {@code Ingredient} JSON. */
    static JsonElement vanillaIngredient(ItemIn in) {
        //? if >=1.21.2 {
        return new JsonPrimitive(in.tag() ? "#" + in.id() : in.id());
        //?} else {
        /*JsonObject json = new JsonObject();
        json.addProperty(in.tag() ? "tag" : "item", in.id());
        return json;
        *///?}
    }

    /** An item stack as this node's vanilla {@code ItemStack} JSON. */
    static JsonObject vanillaStack(String id, int count) {
        JsonObject json = new JsonObject();
        //? if >=1.20.5 {
        json.addProperty("id", id);
        //?} else {
        /*json.addProperty("item", id);
        *///?}
        json.addProperty("count", count);
        return json;
    }
}
//?}
