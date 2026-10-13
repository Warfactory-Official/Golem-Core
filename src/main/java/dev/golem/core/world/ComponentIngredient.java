package dev.golem.core.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.golem.core.Ids;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * A crafting ingredient that can say what the flattened 1.7 ingredients said.
 *
 * <ul>
 *   <li>{@code items}: item ids, and item tags written {@code #namespace:path}; any one matches.</li>
 *   <li>{@code components} / {@code absent_components}: exact components the stack must carry, or must not
 *       (1.7 metadata and NBT flattened into components). A number matches an integer component; a string
 *       matches a component whose codec writes that string (a registry name such as {@code hbm:water} -
 *       never a numeric registry id, which is not stable across saves).</li>
 *   <li>{@code fluid} + {@code fluid_amount}: a full fluid container of that fluid and amount, whatever
 *       the container, per the mod's {@link FilledContainers} dictionary (1.7 {@code getDict}). The grid
 *       keeps the emptied container.</li>
 *   <li>{@code potion}: the potion the stack carries ({@code minecraft:water} is 1.7's potion meta 0).</li>
 *   <li>{@code remainder}: an explicit item the grid keeps instead of the item's own remainder.</li>
 *   <li>{@code degrade}: the stack itself stays in the grid one point more worn (1.7 crafting tools).</li>
 * </ul>
 * Every constraint given has to hold. With no {@code items}, the other constraints alone decide.
 */
public final class ComponentIngredient {

    public static final ComponentIngredient EMPTY =
            new ComponentIngredient(List.of(), Map.of(), List.of(), "", "", 0, "", false);

    /** A component value as recipe data writes it: a number or a string. */
    private static final Codec<Either<Integer, String>> VALUE = Codec.either(Codec.INT, Codec.STRING);

    public static final Codec<ComponentIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(value -> value.items),
            Codec.unboundedMap(Codec.STRING, VALUE).optionalFieldOf("components", Map.of())
                    .forGetter(ComponentIngredient::componentValues),
            Codec.STRING.listOf().optionalFieldOf("absent_components", List.of())
                    .forGetter(value -> value.absentComponents),
            Codec.STRING.optionalFieldOf("remainder", "").forGetter(value -> value.remainder),
            Codec.STRING.optionalFieldOf("fluid", "").forGetter(value -> value.fluid),
            Codec.INT.optionalFieldOf("fluid_amount", 0).forGetter(value -> value.fluidAmount),
            Codec.STRING.optionalFieldOf("potion", "").forGetter(value -> value.potion),
            Codec.BOOL.optionalFieldOf("degrade", false).forGetter(value -> value.degrade)
    ).apply(instance, (items, values, absent, remainder, fluid, amount, potion, degrade) -> new ComponentIngredient(
            items, ints(values), absent, remainder, fluid, amount, potion, degrade, strings(values))));

    //? if >=1.21.1 {
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ComponentIngredient>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of(
                    (buffer, value) -> value.toNetwork(buffer), ComponentIngredient::fromNetwork);
    //?}

    private final List<String> items;
    private final Map<String, Integer> components;
    private final Map<String, String> stringComponents;
    private final List<String> absentComponents;
    private final String remainder;
    private final String fluid;
    private final int fluidAmount;
    private final String potion;
    private final boolean degrade;

    private ComponentIngredient(List<String> items, Map<String, Integer> components,
                                List<String> absentComponents, String remainder, String fluid, int fluidAmount,
                                String potion, boolean degrade) {
        this(items, components, absentComponents, remainder, fluid, fluidAmount, potion, degrade, Map.of());
    }

    private ComponentIngredient(List<String> items, Map<String, Integer> components,
                                List<String> absentComponents, String remainder, String fluid, int fluidAmount,
                                String potion, boolean degrade, Map<String, String> stringComponents) {
        this.items = List.copyOf(items);
        this.components = Map.copyOf(components);
        this.stringComponents = Map.copyOf(stringComponents);
        this.absentComponents = List.copyOf(absentComponents);
        this.remainder = remainder == null ? "" : remainder;
        this.fluid = fluid == null ? "" : fluid;
        this.fluidAmount = fluidAmount;
        this.potion = potion == null ? "" : potion;
        this.degrade = degrade;
        if (this.items.isEmpty() && this.fluid.isEmpty()
                && (!this.components.isEmpty() || !this.stringComponents.isEmpty() || !this.absentComponents.isEmpty()
                || !this.potion.isEmpty())) {
            throw new IllegalArgumentException("component ingredient constrains no item");
        }
        if (!this.fluid.isEmpty() && this.fluidAmount <= 0) {
            throw new IllegalArgumentException("fluid ingredient " + this.fluid + " needs a positive fluid_amount");
        }
    }

    private static Map<String, Integer> ints(Map<String, Either<Integer, String>> values) {
        Map<String, Integer> ints = new HashMap<>();
        values.forEach((key, value) -> value.ifLeft(number -> ints.put(key, number)));
        return ints;
    }

    private static Map<String, String> strings(Map<String, Either<Integer, String>> values) {
        Map<String, String> strings = new HashMap<>();
        values.forEach((key, value) -> value.ifRight(text -> strings.put(key, text)));
        return strings;
    }

    private Map<String, Either<Integer, String>> componentValues() {
        Map<String, Either<Integer, String>> values = new LinkedHashMap<>();
        components.forEach((key, value) -> values.put(key, Either.left(value)));
        stringComponents.forEach((key, value) -> values.put(key, Either.right(value)));
        return values;
    }

    public static ComponentIngredient of(String item) {
        return new ComponentIngredient(List.of(item), Map.of(), List.of(), "", "", 0, "", false);
    }

    /** Any full container of {@code amount} of {@code fluid}; the grid keeps the emptied container. */
    public static ComponentIngredient fluid(String fluid, int amount) {
        return new ComponentIngredient(List.of(), Map.of(), List.of(), "", fluid, amount, "", false);
    }

    public static ComponentIngredient fromJson(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return of(element.getAsString());
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement itemValues = object.get("items");
        List<String> items = new ArrayList<>();
        if (itemValues != null && itemValues.isJsonArray()) {
            for (JsonElement value : itemValues.getAsJsonArray()) items.add(value.getAsString());
        } else if (object.has("item")) {
            items.add(object.get("item").getAsString());
        } else if (object.has("tag")) {
            items.add("#" + object.get("tag").getAsString());
        }
        Map<String, Integer> components = new HashMap<>();
        Map<String, String> stringComponents = new HashMap<>();
        JsonObject componentValues = object.getAsJsonObject("components");
        if (componentValues != null) {
            for (Map.Entry<String, JsonElement> entry : componentValues.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    stringComponents.put(entry.getKey(), value.getAsString());
                } else {
                    components.put(entry.getKey(), value.getAsInt());
                }
            }
        }
        List<String> absentComponents = new ArrayList<>();
        JsonArray absentValues = object.getAsJsonArray("absent_components");
        if (absentValues != null) {
            for (JsonElement value : absentValues) absentComponents.add(value.getAsString());
        }
        return new ComponentIngredient(items, components, absentComponents,
                string(object, "remainder"), string(object, "fluid"),
                object.has("fluid_amount") ? object.get("fluid_amount").getAsInt() : 0,
                string(object, "potion"), object.has("degrade") && object.get("degrade").getAsBoolean(),
                stringComponents);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    public static ComponentIngredient fromNetwork(FriendlyByteBuf buffer) {
        int itemCount = buffer.readVarInt();
        List<String> items = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) items.add(buffer.readUtf());
        int componentCount = buffer.readVarInt();
        Map<String, Integer> components = new HashMap<>();
        for (int i = 0; i < componentCount; i++) components.put(buffer.readUtf(), buffer.readVarInt());
        int stringCount = buffer.readVarInt();
        Map<String, String> stringComponents = new HashMap<>();
        for (int i = 0; i < stringCount; i++) stringComponents.put(buffer.readUtf(), buffer.readUtf());
        int absentCount = buffer.readVarInt();
        List<String> absentComponents = new ArrayList<>(absentCount);
        for (int i = 0; i < absentCount; i++) absentComponents.add(buffer.readUtf());
        String remainder = buffer.readUtf();
        String fluid = buffer.readUtf();
        int fluidAmount = buffer.readVarInt();
        String potion = buffer.readUtf();
        boolean degrade = buffer.readBoolean();
        return new ComponentIngredient(items, components, absentComponents, remainder, fluid, fluidAmount,
                potion, degrade, stringComponents);
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        buffer.writeVarInt(items.size());
        for (String item : items) buffer.writeUtf(item);
        buffer.writeVarInt(components.size());
        for (Map.Entry<String, Integer> entry : components.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
        buffer.writeVarInt(stringComponents.size());
        for (Map.Entry<String, String> entry : stringComponents.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
        buffer.writeVarInt(absentComponents.size());
        for (String key : absentComponents) buffer.writeUtf(key);
        buffer.writeUtf(remainder);
        buffer.writeUtf(fluid);
        buffer.writeVarInt(fluidAmount);
        buffer.writeUtf(potion);
        buffer.writeBoolean(degrade);
    }

    public List<String> items() {
        return items;
    }

    public Map<String, Integer> components() {
        return components;
    }

    /** The string-valued components the stack must carry (registry names), keyed by component id. */
    public Map<String, String> stringComponents() {
        return stringComponents;
    }

    public List<String> absentComponents() {
        return absentComponents;
    }

    public String remainder() {
        return remainder;
    }

    public String fluid() {
        return fluid;
    }

    public int fluidAmount() {
        return fluidAmount;
    }

    public String potion() {
        return potion;
    }

    public boolean degrade() {
        return degrade;
    }

    public boolean isEmpty() {
        return items.isEmpty() && fluid.isEmpty();
    }

    public boolean test(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isEmpty()) return false;
        if (!items.isEmpty() && items.stream().noneMatch(entry -> matchesEntry(stack, entry))) return false;
        if (!fluid.isEmpty() && !FilledContainers.holds(stack, fluid, fluidAmount)) return false;
        if (!potion.isEmpty() && !potion.equals(Ingredients.potionId(stack))) return false;
        return components.entrySet().stream()
                .allMatch(entry -> ItemStacks.matchesIntComponent(stack, entry.getKey(), entry.getValue()))
                && stringComponents.entrySet().stream()
                .allMatch(entry -> ItemStacks.matchesStringComponent(stack, entry.getKey(), entry.getValue()))
                && absentComponents.stream()
                .allMatch(key -> ItemStacks.matchesAbsentComponent(stack, key));
    }

    private static boolean matchesEntry(ItemStack stack, String entry) {
        return entry.startsWith("#") ? Ingredients.matchesTag(stack, entry.substring(1))
                : ItemStacks.matchesItemId(stack, entry);
    }

    /**
     * What the crafting grid keeps in place of {@code consumed}: the emptied container for a fluid
     * ingredient, the worn tool for a degrading one, the explicit {@code remainder}, or else the item's
     * own crafting remainder.
     */
    public ItemStack remainderFor(ItemStack consumed) {
        if (!fluid.isEmpty()) return FilledContainers.emptied(consumed);
        if (degrade) return Ingredients.degraded(consumed);
        if (!remainder.isEmpty()) {
            Item item = BuiltInRegistries.ITEM.getValue(Ids.parse(remainder));
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        }
        return Compat.craftingRemainder(consumed);
    }

    /** Stacks this ingredient would accept, for recipe displays (components shown where the version can). */
    public List<ItemStack> displayStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        if (items.isEmpty()) {
            if (!fluid.isEmpty()) stacks.addAll(FilledContainers.examples(fluid, fluidAmount));
            return stacks;
        }
        for (String entry : items) {
            if (entry.startsWith("#")) {
                for (Item item : Ingredients.tagItems(entry.substring(1))) stacks.add(new ItemStack(item));
            } else if (!potion.isEmpty()) {
                stacks.add(Ingredients.potionStack(entry, potion));
            } else {
                Item item = BuiltInRegistries.ITEM.getValue(Ids.parse(entry));
                if (item != Items.AIR) stacks.add(new ItemStack(item));
            }
        }
        return stacks;
    }

    /** A display-only ordinary ingredient; the constraints remain enforced by {@link #test}. */
    public Ingredient displayIngredient() {
        if (isEmpty()) return Ingredient.of();
        if (items.size() == 1 && items.get(0).startsWith("#") && fluid.isEmpty()) {
            // A lone tag stays a lazily bound tag, so it displays correctly even before tags are loaded.
            TagKey<Item> tag = TagKey.create(
                    Registries.ITEM, Ids.parse(items.get(0).substring(1)));
            //? if >=26.1 {
            java.util.Optional<net.minecraft.core.HolderSet.Named<Item>> set =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(tag);
            if (set.isPresent()) return Ingredient.of(set.get());
            //?} else {
            /*return Ingredient.of(tag);
            *///?}
        }
        //? if >=26.1 {
        // 26.1 decodes recipes before item components are bound: no ItemStack may be built here for a plain
        // item or tag (ComponentSmeltingRecipe calls this from its constructor). Only a fluid ingredient needs
        // the dictionary's example stacks, and that is asked for after load (placement info, displays).
        List<Item> display = new ArrayList<>();
        if (items.isEmpty()) {
            for (ItemStack example : FilledContainers.examples(fluid, fluidAmount)) display.add(example.getItem());
        }
        for (String entry : items) {
            if (entry.startsWith("#")) {
                display.addAll(Ingredients.tagItems(entry.substring(1)));
            } else {
                Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Ids.parse(entry));
                if (item != Items.AIR) display.add(item);
            }
        }
        if (display.isEmpty()) display.add(Items.BARRIER);
        return Ingredient.of(display.stream().distinct());
        //?} else {
        /*List<ItemStack> stacks = displayStacks();
        if (stacks.isEmpty()) stacks = List.of(new ItemStack(Items.BARRIER));
        return Ingredient.of(stacks.toArray(ItemStack[]::new));
        *///?}
    }
}
