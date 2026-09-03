package dev.golem.core.world;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Constructors for custom-recipe serializers across vanilla's codec and legacy serializer APIs. */
public final class CustomRecipes {

    private CustomRecipes() {}

    public static <T extends CompatCustomRecipe> RecipeSerializer<T> serializer(Supplier<T> recipe) {
        //? if >=26.1 {
        return new RecipeSerializer<>(com.mojang.serialization.MapCodec.unit(recipe.get()),
                net.minecraft.network.codec.StreamCodec.unit(recipe.get()));
        //?} else if >=1.21.1 {
        /*return new RecipeSerializer<>() {
            private final com.mojang.serialization.MapCodec<T> codec = com.mojang.serialization.MapCodec.unit(recipe.get());
            private final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> stream = net.minecraft.network.codec.StreamCodec.unit(recipe.get());
            @Override public com.mojang.serialization.MapCodec<T> codec() { return codec; }
            @Override public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> streamCodec() { return stream; }
        };
        */
        //?} else {
        /*return new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>((id, category) -> recipe.get());
        *///?}
    }

    /** Factory used by the data-driven shapeless component-copy serializer. */
    @FunctionalInterface
    public interface ComponentCopyFactory<T extends ComponentCopyShapelessRecipe> {
        T create(String namespace, String path, List<Ingredient> ingredients, Ingredient copyFrom,
                 ItemStackSpec result);
    }

    /**
     * Builds the one serializer shape shared by component-era codecs and the 1.20.1 serializer API.
     * The recipe's fields deliberately remain ordinary crafting fields; {@code copy_from} is the only
     * additional field.
     */
    public static <T extends ComponentCopyShapelessRecipe> RecipeSerializer<T> componentCopyShapeless(
            ComponentCopyFactory<T> factory) {
        Objects.requireNonNull(factory);
        //? if >=26.1 {
        return componentCopyShapeless26(factory);
        //?} else if >=1.21.1 {
        /*return componentCopyShapeless21(factory);
        *///?} else {
        /*return componentCopyShapelessLegacy(factory);
        *///?}
    }

    /** Factory for a shaped recipe whose Ingredient values may constrain item components. */
    @FunctionalInterface
    public interface ComponentShapedFactory<T extends ComponentShapedRecipe> {
        T create(String namespace, String path, List<String> pattern,
                 java.util.Map<String, ComponentIngredient> key, ItemStackSpec result);
    }

    /** Builds the cross-version component-sensitive shaped recipe serializer. */
    public static <T extends ComponentShapedRecipe> RecipeSerializer<T> componentShaped(
            ComponentShapedFactory<T> factory) {
        Objects.requireNonNull(factory);
        //? if >=26.1 {
        return componentShaped26(factory);
        //?} else if >=1.21.1 {
        /*return componentShaped21(factory);
        *///?} else {
        /*return componentShapedLegacy(factory);
        *///?}
    }

    //? if >=26.1 {
    private static <T extends ComponentShapedRecipe> RecipeSerializer<T> componentShaped26(
            ComponentShapedFactory<T> factory) {
        com.mojang.serialization.Codec<java.util.Map<String, ComponentIngredient>> keyCodec =
                com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,
                        ComponentIngredient.CODEC);
        com.mojang.serialization.MapCodec<T> codec =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        com.mojang.serialization.Codec.STRING.listOf().fieldOf("pattern")
                                .forGetter(recipe -> recipe.pattern()),
                        keyCodec.fieldOf("key").forGetter(recipe -> recipe.key()),
                        net.minecraft.world.item.ItemStackTemplate.CODEC.fieldOf("result")
                                .forGetter(recipe -> recipe.resultTemplate())
                ).apply(instance, (pattern, key, result) ->
                        factory.create("", "", pattern, key, ItemStackSpec.fromTemplate(result))));
        net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> stream =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(
                                net.minecraft.network.codec.ByteBufCodecs.list()),
                        recipe -> recipe.pattern(),
                        net.minecraft.network.codec.ByteBufCodecs.map(java.util.HashMap::new,
                                net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                                ComponentIngredient.STREAM_CODEC),
                        recipe -> recipe.key(),
                        net.minecraft.world.item.ItemStackTemplate.STREAM_CODEC,
                        recipe -> recipe.resultTemplate(),
                        (pattern, key, result) -> factory.create("", "", pattern, key,
                                ItemStackSpec.fromTemplate(result)));
        return new RecipeSerializer<>(codec, stream);
    }
    //?}

    //? if >=1.21.1 && <26.1 {
    /*private static <T extends ComponentShapedRecipe> RecipeSerializer<T> componentShaped21(
            ComponentShapedFactory<T> factory) {
        com.mojang.serialization.Codec<java.util.Map<String, ComponentIngredient>> keyCodec =
                com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,
                        ComponentIngredient.CODEC);
        com.mojang.serialization.MapCodec<T> codec =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        com.mojang.serialization.Codec.STRING.listOf().fieldOf("pattern")
                                .forGetter(recipe -> recipe.pattern()),
                        keyCodec.fieldOf("key").forGetter(recipe -> recipe.key()),
                        ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.result())
                ).apply(instance, (pattern, key, result) ->
                        factory.create("", "", pattern, key, ItemStackSpec.of(result))));
        net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> stream =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(
                                net.minecraft.network.codec.ByteBufCodecs.list()),
                        recipe -> recipe.pattern(),
                        net.minecraft.network.codec.ByteBufCodecs.map(java.util.HashMap::new,
                                net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                                ComponentIngredient.STREAM_CODEC),
                        recipe -> recipe.key(),
                        ItemStack.STREAM_CODEC,
                        recipe -> recipe.result(),
                        (pattern, key, result) -> factory.create("", "", pattern, key,
                                ItemStackSpec.of(result)));
        return new RecipeSerializer<>() {
            @Override public com.mojang.serialization.MapCodec<T> codec() { return codec; }
            @Override public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> streamCodec() {
                return stream;
            }
        };
    }
    *///?}

    //? if <1.21.1 {
    /*private static <T extends ComponentShapedRecipe> RecipeSerializer<T> componentShapedLegacy(
            ComponentShapedFactory<T> factory) {
        return new RecipeSerializer<>() {
            @Override
            public T fromJson(net.minecraft.resources.ResourceLocation id, com.google.gson.JsonObject json) {
                com.google.gson.JsonArray jsonPattern =
                        net.minecraft.util.GsonHelper.getAsJsonArray(json, "pattern");
                List<String> pattern = new java.util.ArrayList<>(jsonPattern.size());
                for (com.google.gson.JsonElement element : jsonPattern) pattern.add(element.getAsString());

                com.google.gson.JsonObject jsonKey =
                        net.minecraft.util.GsonHelper.getAsJsonObject(json, "key");
                java.util.Map<String, ComponentIngredient> key = new java.util.HashMap<>();
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : jsonKey.entrySet()) {
                    key.put(entry.getKey(), ComponentIngredient.fromJson(entry.getValue()));
                }
                ItemStack result = net.minecraft.world.item.crafting.ShapedRecipe.itemStackFromJson(
                        net.minecraft.util.GsonHelper.getAsJsonObject(json, "result"));
                return factory.create(id.getNamespace(), id.getPath(), pattern, key, ItemStackSpec.of(result));
            }

            @Override
            public T fromNetwork(net.minecraft.resources.ResourceLocation id,
                                 net.minecraft.network.FriendlyByteBuf buffer) {
                int patternSize = buffer.readVarInt();
                List<String> pattern = new java.util.ArrayList<>(patternSize);
                for (int i = 0; i < patternSize; i++) pattern.add(buffer.readUtf());
                int keySize = buffer.readVarInt();
                java.util.Map<String, ComponentIngredient> key = new java.util.HashMap<>();
                for (int i = 0; i < keySize; i++) key.put(buffer.readUtf(), ComponentIngredient.fromNetwork(buffer));
                return factory.create(id.getNamespace(), id.getPath(), pattern, key,
                        ItemStackSpec.of(buffer.readItem()));
            }

            @Override
            public void toNetwork(net.minecraft.network.FriendlyByteBuf buffer, T recipe) {
                buffer.writeVarInt(recipe.pattern().size());
                for (String row : recipe.pattern()) buffer.writeUtf(row);
                buffer.writeVarInt(recipe.key().size());
                for (java.util.Map.Entry<String, ComponentIngredient> entry : recipe.key().entrySet()) {
                    buffer.writeUtf(entry.getKey());
                    entry.getValue().toNetwork(buffer);
                }
                buffer.writeItem(recipe.result());
            }

        };
    }
    *///?}

    //? if >=26.1 {
    private static <T extends ComponentCopyShapelessRecipe> RecipeSerializer<T> componentCopyShapeless26(
            ComponentCopyFactory<T> factory) {
        com.mojang.serialization.MapCodec<T> codec =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC.listOf().fieldOf("ingredients")
                                .forGetter(recipe -> recipe.ingredients()),
                        Ingredient.CODEC.fieldOf("copy_from")
                                .forGetter(recipe -> recipe.copyFrom()),
                        net.minecraft.world.item.ItemStackTemplate.CODEC.fieldOf("result")
                                .forGetter(recipe -> recipe.resultTemplate())
                ).apply(instance, (ingredients, copyFrom, result) ->
                        factory.create("", "", ingredients, copyFrom, ItemStackSpec.fromTemplate(result))));
        net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> stream =
                net.minecraft.network.codec.StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()),
                        recipe -> recipe.ingredients(),
                        Ingredient.CONTENTS_STREAM_CODEC,
                        recipe -> recipe.copyFrom(),
                        net.minecraft.world.item.ItemStackTemplate.STREAM_CODEC,
                        recipe -> recipe.resultTemplate(),
                        (ingredients, copyFrom, result) ->
                                factory.create("", "", ingredients, copyFrom, ItemStackSpec.fromTemplate(result)));
        return new RecipeSerializer<>(codec, stream);
    }
    //?}

    //? if >=1.21.1 && <26.1 {
    /*private static <T extends ComponentCopyShapelessRecipe> RecipeSerializer<T> componentCopyShapeless21(
            ComponentCopyFactory<T> factory) {
        com.mojang.serialization.MapCodec<T> codec =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC.listOf().fieldOf("ingredients")
                                .forGetter(recipe -> recipe.ingredients()),
                        Ingredient.CODEC.fieldOf("copy_from")
                                .forGetter(recipe -> recipe.copyFrom()),
                        ItemStack.CODEC.fieldOf("result")
                                .forGetter(recipe -> recipe.result())
                ).apply(instance, (ingredients, copyFrom, result) ->
                        factory.create("", "", ingredients, copyFrom, ItemStackSpec.of(result))));
        net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> stream =
                net.minecraft.network.codec.StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()),
                        recipe -> recipe.ingredients(),
                        Ingredient.CONTENTS_STREAM_CODEC,
                        recipe -> recipe.copyFrom(),
                        ItemStack.STREAM_CODEC,
                        recipe -> recipe.result(),
                        (ingredients, copyFrom, result) ->
                                factory.create("", "", ingredients, copyFrom, ItemStackSpec.of(result)));
        return new RecipeSerializer<>() {
            @Override public com.mojang.serialization.MapCodec<T> codec() { return codec; }
            @Override public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> streamCodec() {
                return stream;
            }
        };
    }
    *///?}

    //? if <1.21.1 {
    /*private static <T extends ComponentCopyShapelessRecipe> RecipeSerializer<T> componentCopyShapelessLegacy(
            ComponentCopyFactory<T> factory) {
        return new RecipeSerializer<>() {
            @Override
            public T fromJson(net.minecraft.resources.ResourceLocation id, com.google.gson.JsonObject json) {
                com.google.gson.JsonArray jsonIngredients =
                        net.minecraft.util.GsonHelper.getAsJsonArray(json, "ingredients");
                List<Ingredient> ingredients = new java.util.ArrayList<>(jsonIngredients.size());
                for (com.google.gson.JsonElement element : jsonIngredients) {
                    ingredients.add(legacyIngredient(element));
                }
                com.google.gson.JsonElement copyElement = json.get("copy_from");
                if (copyElement == null) throw new com.google.gson.JsonSyntaxException("Missing copy_from");
                Ingredient copyFrom = legacyIngredient(copyElement);
                ItemStack result = net.minecraft.world.item.crafting.ShapedRecipe.itemStackFromJson(
                        net.minecraft.util.GsonHelper.getAsJsonObject(json, "result"));
                return factory.create(id.getNamespace(), id.getPath(), ingredients, copyFrom, ItemStackSpec.of(result));
            }

            @Override
            public T fromNetwork(net.minecraft.resources.ResourceLocation id, net.minecraft.network.FriendlyByteBuf buffer) {
                int size = buffer.readVarInt();
                List<Ingredient> ingredients = new java.util.ArrayList<>(size);
                for (int i = 0; i < size; i++) ingredients.add(Ingredient.fromNetwork(buffer));
                Ingredient copyFrom = Ingredient.fromNetwork(buffer);
                ItemStack result = buffer.readItem();
                return factory.create(id.getNamespace(), id.getPath(), ingredients, copyFrom, ItemStackSpec.of(result));
            }

            @Override
            public void toNetwork(net.minecraft.network.FriendlyByteBuf buffer, T recipe) {
                buffer.writeVarInt(recipe.ingredients().size());
                for (Ingredient ingredient : recipe.ingredients()) ingredient.toNetwork(buffer);
                recipe.copyFrom().toNetwork(buffer);
                buffer.writeItem(recipe.result());
            }

            private Ingredient legacyIngredient(com.google.gson.JsonElement element) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    String value = element.getAsString();
                    com.google.gson.JsonObject object = new com.google.gson.JsonObject();
                    if (value.startsWith("#")) object.addProperty("tag", value.substring(1));
                    else object.addProperty("item", value);
                    element = object;
                }
                return Ingredient.fromJson(element);
            }
        };
    }
    *///?}
}
