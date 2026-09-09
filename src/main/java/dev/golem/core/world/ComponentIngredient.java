package dev.golem.core.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.golem.core.Ids;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** An item ingredient with exact integer component constraints. */
public final class ComponentIngredient {

    public static final ComponentIngredient EMPTY = new ComponentIngredient(List.of(), Map.of(), List.of(), "");

    public static final Codec<ComponentIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().fieldOf("items").forGetter(value -> value.items),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("components", Map.of())
                    .forGetter(value -> value.components),
            Codec.STRING.listOf().optionalFieldOf("absent_components", List.of())
                    .forGetter(value -> value.absentComponents),
            Codec.STRING.optionalFieldOf("remainder", "").forGetter(value -> value.remainder)
    ).apply(instance, ComponentIngredient::new));

    //? if >=1.21.1 {
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ComponentIngredient>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(
                            net.minecraft.network.codec.ByteBufCodecs.list()),
                     value -> value.items,
                     net.minecraft.network.codec.ByteBufCodecs.map(java.util.HashMap::new,
                             net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                             net.minecraft.network.codec.ByteBufCodecs.INT),
                      value -> value.components,
                      net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(
                              net.minecraft.network.codec.ByteBufCodecs.list()),
                      value -> value.absentComponents,
                      net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                      value -> value.remainder,
                      ComponentIngredient::new);
    //?}

    private final List<String> items;
    private final Map<String, Integer> components;
    private final List<String> absentComponents;
    private final String remainder;

    private ComponentIngredient(List<String> items, Map<String, Integer> components,
                                List<String> absentComponents, String remainder) {
        if (items.isEmpty() && (!components.isEmpty() || !absentComponents.isEmpty())) {
            throw new IllegalArgumentException("empty component ingredient");
        }
        this.items = List.copyOf(items);
        this.components = Map.copyOf(components);
        this.absentComponents = List.copyOf(absentComponents);
        this.remainder = remainder == null ? "" : remainder;
    }

    public static ComponentIngredient of(String item) {
        return new ComponentIngredient(List.of(item), Map.of(), List.of(), "");
    }

    public static ComponentIngredient fromJson(com.google.gson.JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return of(element.getAsString());
        }
        com.google.gson.JsonObject object = element.getAsJsonObject();
        com.google.gson.JsonElement itemValues = object.get("items");
        List<String> items = new java.util.ArrayList<>();
        if (itemValues != null && itemValues.isJsonArray()) {
            for (com.google.gson.JsonElement value : itemValues.getAsJsonArray()) items.add(value.getAsString());
        } else if (object.has("item")) {
            items.add(object.get("item").getAsString());
        }
        Map<String, Integer> components = new java.util.HashMap<>();
        com.google.gson.JsonObject componentValues = object.getAsJsonObject("components");
        if (componentValues != null) {
            for (Map.Entry<String, com.google.gson.JsonElement> entry : componentValues.entrySet()) {
                components.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }
        List<String> absentComponents = new java.util.ArrayList<>();
        com.google.gson.JsonArray absentValues = object.getAsJsonArray("absent_components");
        if (absentValues != null) {
            for (com.google.gson.JsonElement value : absentValues) absentComponents.add(value.getAsString());
        }
        String remainder = object.has("remainder") ? object.get("remainder").getAsString() : "";
        return new ComponentIngredient(items, components, absentComponents, remainder);
    }

    //? if <1.21.1 {
    /*public static ComponentIngredient fromNetwork(net.minecraft.network.FriendlyByteBuf buffer) {
        int itemCount = buffer.readVarInt();
        List<String> items = new java.util.ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) items.add(buffer.readUtf());
        int componentCount = buffer.readVarInt();
        Map<String, Integer> components = new java.util.HashMap<>();
        for (int i = 0; i < componentCount; i++) components.put(buffer.readUtf(), buffer.readVarInt());
        int absentCount = buffer.readVarInt();
        List<String> absentComponents = new java.util.ArrayList<>(absentCount);
        for (int i = 0; i < absentCount; i++) absentComponents.add(buffer.readUtf());
        return new ComponentIngredient(items, components, absentComponents, buffer.readUtf());
    }

    public void toNetwork(net.minecraft.network.FriendlyByteBuf buffer) {
        buffer.writeVarInt(items.size());
        for (String item : items) buffer.writeUtf(item);
        buffer.writeVarInt(components.size());
        for (Map.Entry<String, Integer> entry : components.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
        buffer.writeVarInt(absentComponents.size());
        for (String key : absentComponents) buffer.writeUtf(key);
        buffer.writeUtf(remainder);
    }
    *///?}

    public List<String> items() {
        return items;
    }

    public Map<String, Integer> components() {
        return components;
    }

    public List<String> absentComponents() {
        return absentComponents;
    }

    public String remainder() {
        return remainder;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean test(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !items.stream().anyMatch(item -> ItemStacks.matchesItemId(stack, item))) {
            return false;
        }
        return components.entrySet().stream()
                .allMatch(entry -> ItemStacks.matchesIntComponent(stack, entry.getKey(), entry.getValue()))
                && absentComponents.stream()
                .allMatch(key -> ItemStacks.matchesAbsentComponent(stack, key));
    }

    /** A display-only ordinary ingredient; the component constraint remains enforced by {@link #test}. */
    public Ingredient displayIngredient() {
        if (items.isEmpty()) return Ingredient.of();
        //? if >=26.1 {
        return Ingredient.of(items.stream()
                .map(id -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Ids.parse(id))));
        //?} else {
        /*return Ingredient.of(items.stream()
                .map(id -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Ids.parse(id)))));
        *///?}
    }
}
