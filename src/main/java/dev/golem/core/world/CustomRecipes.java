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
                 java.util.Map<String, ComponentIngredient> key, ItemStackSpec result, String copyFrom);
    }

    /** Factory for a shapeless recipe over component-constrained ingredients. */
    @FunctionalInterface
    public interface ComponentShapelessFactory<T extends ComponentShapelessRecipe> {
        T create(String namespace, String path, List<ComponentIngredient> ingredients, ItemStackSpec result);
    }

    /** Builds the cross-version component-sensitive shapeless recipe serializer. */
    public static <T extends ComponentShapelessRecipe> RecipeSerializer<T> componentShapeless(
            ComponentShapelessFactory<T> factory) {
        Objects.requireNonNull(factory);
        //? if >=26.1 {
        com.mojang.serialization.MapCodec<T> codec =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ComponentIngredient.CODEC.listOf().fieldOf("ingredients")
                                .forGetter(recipe -> recipe.ingredients()),
                        net.minecraft.world.item.ItemStackTemplate.CODEC.fieldOf("result")
                                .forGetter(recipe -> recipe.resultTemplate())
                ).apply(instance, (ingredients, result) ->
                        factory.create("", "", ingredients, ItemStackSpec.fromTemplate(result))));
        net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> stream =
                net.minecraft.network.codec.StreamCodec.composite(
                        ComponentIngredient.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()),
                        recipe -> recipe.ingredients(),
                        net.minecraft.world.item.ItemStackTemplate.STREAM_CODEC,
                        recipe -> recipe.resultTemplate(),
                        (ingredients, result) -> factory.create("", "", ingredients, ItemStackSpec.fromTemplate(result)));
        return new RecipeSerializer<>(codec, stream);
        //?} else if >=1.21.1 {
        /*com.mojang.serialization.MapCodec<T> codec =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ComponentIngredient.CODEC.listOf().fieldOf("ingredients")
                                .forGetter(recipe -> recipe.ingredients()),
                        ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.result())
                ).apply(instance, (ingredients, result) ->
                        factory.create("", "", ingredients, ItemStackSpec.of(result))));
        net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> stream =
                net.minecraft.network.codec.StreamCodec.composite(
                        ComponentIngredient.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()),
                        recipe -> recipe.ingredients(),
                        ItemStack.STREAM_CODEC,
                        recipe -> recipe.result(),
                        (ingredients, result) -> factory.create("", "", ingredients, ItemStackSpec.of(result)));
        return new RecipeSerializer<>() {
            @Override public com.mojang.serialization.MapCodec<T> codec() { return codec; }
            @Override public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> streamCodec() {
                return stream;
            }
        };
        *///?} else {
        /*return new RecipeSerializer<>() {
            @Override
            public T fromJson(net.minecraft.resources.ResourceLocation id, com.google.gson.JsonObject json) {
                com.google.gson.JsonArray jsonIngredients =
                        net.minecraft.util.GsonHelper.getAsJsonArray(json, "ingredients");
                List<ComponentIngredient> ingredients = new java.util.ArrayList<>(jsonIngredients.size());
                for (com.google.gson.JsonElement element : jsonIngredients) {
                    ingredients.add(ComponentIngredient.fromJson(element));
                }
                ItemStack result = legacyResult(net.minecraft.util.GsonHelper.getAsJsonObject(json, "result"));
                return factory.create(id.getNamespace(), id.getPath(), ingredients, ItemStackSpec.of(result));
            }

            @Override
            public T fromNetwork(net.minecraft.resources.ResourceLocation id, net.minecraft.network.FriendlyByteBuf buffer) {
                int size = buffer.readVarInt();
                List<ComponentIngredient> ingredients = new java.util.ArrayList<>(size);
                for (int i = 0; i < size; i++) ingredients.add(ComponentIngredient.fromNetwork(buffer));
                return factory.create(id.getNamespace(), id.getPath(), ingredients, ItemStackSpec.of(buffer.readItem()));
            }

            @Override
            public void toNetwork(net.minecraft.network.FriendlyByteBuf buffer, T recipe) {
                buffer.writeVarInt(recipe.ingredients().size());
                for (ComponentIngredient ingredient : recipe.ingredients()) ingredient.toNetwork(buffer);
                buffer.writeItem(recipe.result());
            }
        };
        *///?}
    }


    @FunctionalInterface
    public interface ComponentSmeltingFactory<T extends net.minecraft.world.item.crafting.SmeltingRecipe> {
        T create(String namespace, String path, ComponentIngredient ingredient, ItemStackSpec result,
                 float experience, int cookingTime);
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

    /** Builds a smelting recipe whose input and result retain exact integer item components. */
    public static <T extends net.minecraft.world.item.crafting.SmeltingRecipe> RecipeSerializer<T> componentSmelting(
            ComponentSmeltingFactory<T> factory) {
        Objects.requireNonNull(factory);
        //? if >=26.1 {
        return componentSmelting26(factory);
        //?} else if >=1.21.1 {
        /*return componentSmelting21(factory);
        *///?} else {
        /*return componentSmeltingLegacy(factory);
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
                                .forGetter(recipe -> recipe.resultTemplate()),
                        com.mojang.serialization.Codec.STRING.optionalFieldOf("copy_key", "")
                                .forGetter(recipe -> recipe.copyFrom())
                ).apply(instance, (pattern, key, result, copyFrom) ->
                        factory.create("", "", pattern, key, ItemStackSpec.fromTemplate(result), copyFrom)));
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
                        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                        recipe -> recipe.copyFrom(),
                        (pattern, key, result, copyFrom) -> factory.create("", "", pattern, key,
                                ItemStackSpec.fromTemplate(result), copyFrom));
        return new RecipeSerializer<>(codec, stream);
    }
    //?}

    //? if >=26.1 {
    private static <T extends net.minecraft.world.item.crafting.SmeltingRecipe> RecipeSerializer<T> componentSmelting26(
            ComponentSmeltingFactory<T> factory) {
        com.mojang.serialization.MapCodec<T> codec =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ComponentIngredient.CODEC.fieldOf("ingredient")
                                .forGetter(recipe -> ((ComponentSmeltingRecipe) recipe).componentIngredient()),
                        net.minecraft.world.item.ItemStackTemplate.CODEC.fieldOf("result")
                                .forGetter(recipe -> ((ComponentSmeltingRecipe) recipe).resultTemplate()),
                        com.mojang.serialization.Codec.FLOAT.fieldOf("experience")
                                .forGetter(recipe -> recipe.experience()),
                        com.mojang.serialization.Codec.INT.fieldOf("cookingtime")
                                .forGetter(recipe -> recipe.cookingTime())
                ).apply(instance, (ingredient, result, experience, cookingTime) ->
                         factory.create("", "", ingredient, ItemStackSpec.fromTemplate(result), experience, cookingTime)));
        net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> stream =
                net.minecraft.network.codec.StreamCodec.composite(
                        ComponentIngredient.STREAM_CODEC,
                        recipe -> ((ComponentSmeltingRecipe) recipe).componentIngredient(),
                        net.minecraft.world.item.ItemStackTemplate.STREAM_CODEC,
                        recipe -> ((ComponentSmeltingRecipe) recipe).resultTemplate(),
                        net.minecraft.network.codec.ByteBufCodecs.FLOAT,
                        recipe -> recipe.experience(),
                        net.minecraft.network.codec.ByteBufCodecs.INT,
                        recipe -> recipe.cookingTime(),
                        (ingredient, result, experience, cookingTime) ->
                                 factory.create("", "", ingredient, ItemStackSpec.fromTemplate(result), experience, cookingTime));
        return new RecipeSerializer<>(codec, stream);
    }
    //?}

    //? if >=1.21.1 && <26.1 {
     /*private static <T extends net.minecraft.world.item.crafting.SmeltingRecipe> RecipeSerializer<T> componentSmelting21(
            ComponentSmeltingFactory<T> factory) {
        com.mojang.serialization.MapCodec<T> codec =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ComponentIngredient.CODEC.fieldOf("ingredient")
                                .forGetter(recipe -> ((ComponentSmeltingRecipe) recipe).componentIngredient()),
                        net.minecraft.world.item.ItemStack.CODEC.fieldOf("result")
                                .forGetter(recipe -> ((ComponentSmeltingRecipe) recipe).result()),
                        com.mojang.serialization.Codec.FLOAT.fieldOf("experience")
                                 .forGetter(recipe -> recipe.getExperience()),
                        com.mojang.serialization.Codec.INT.fieldOf("cookingtime")
                                 .forGetter(recipe -> recipe.getCookingTime())
                ).apply(instance, (ingredient, result, experience, cookingTime) ->
                         factory.create("", "", ingredient, ItemStackSpec.of(result), experience, cookingTime)));
        net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> stream =
                net.minecraft.network.codec.StreamCodec.composite(
                        ComponentIngredient.STREAM_CODEC,
                        recipe -> ((ComponentSmeltingRecipe) recipe).componentIngredient(),
                        net.minecraft.world.item.ItemStack.STREAM_CODEC,
                        recipe -> ((ComponentSmeltingRecipe) recipe).result(),
                        net.minecraft.network.codec.ByteBufCodecs.FLOAT,
                         recipe -> recipe.getExperience(),
                        net.minecraft.network.codec.ByteBufCodecs.INT,
                         recipe -> recipe.getCookingTime(),
                        (ingredient, result, experience, cookingTime) ->
                                 factory.create("", "", ingredient, ItemStackSpec.of(result), experience, cookingTime));
        return new RecipeSerializer<>() {
            @Override public com.mojang.serialization.MapCodec<T> codec() { return codec; }
            @Override public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> streamCodec() {
                return stream;
            }
        };
    }
    *///?}

    //? if <1.21.1 {
    /*private static <T extends net.minecraft.world.item.crafting.SmeltingRecipe> RecipeSerializer<T> componentSmeltingLegacy(
            ComponentSmeltingFactory<T> factory) {
        return new RecipeSerializer<>() {
            @Override
            public T fromJson(net.minecraft.resources.ResourceLocation id, com.google.gson.JsonObject json) {
                ComponentIngredient ingredient = ComponentIngredient.fromJson(json.get("ingredient"));
                net.minecraft.world.item.ItemStack result = legacyResult(
                        net.minecraft.util.GsonHelper.getAsJsonObject(json, "result"));
                float experience = net.minecraft.util.GsonHelper.getAsFloat(json, "experience", 0F);
                int cookingTime = net.minecraft.util.GsonHelper.getAsInt(json, "cookingtime", 200);
                 return factory.create(id.getNamespace(), id.getPath(), ingredient, ItemStackSpec.of(result), experience, cookingTime);
            }

            @Override
            public T fromNetwork(net.minecraft.resources.ResourceLocation id,
                                 net.minecraft.network.FriendlyByteBuf buffer) {
                ComponentIngredient ingredient = ComponentIngredient.fromNetwork(buffer);
                net.minecraft.world.item.ItemStack result = buffer.readItem();
                 return factory.create(id.getNamespace(), id.getPath(), ingredient, ItemStackSpec.of(result), buffer.readFloat(), buffer.readVarInt());
            }

            @Override
            public void toNetwork(net.minecraft.network.FriendlyByteBuf buffer, T recipe) {
                ComponentSmeltingRecipe component = (ComponentSmeltingRecipe) recipe;
                component.componentIngredient().toNetwork(buffer);
                buffer.writeItem(component.result());
                 buffer.writeFloat(component.getExperience());
                 buffer.writeVarInt(component.getCookingTime());
            }
        };
    }
    *///?}

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
                        ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.result()),
                        com.mojang.serialization.Codec.STRING.optionalFieldOf("copy_key", "")
                                .forGetter(recipe -> recipe.copyFrom())
                ).apply(instance, (pattern, key, result, copyFrom) ->
                        factory.create("", "", pattern, key, ItemStackSpec.of(result), copyFrom)));
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
                        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                        recipe -> recipe.copyFrom(),
                        (pattern, key, result, copyFrom) -> factory.create("", "", pattern, key,
                                ItemStackSpec.of(result), copyFrom));
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
                ItemStack result = legacyResult(
                        net.minecraft.util.GsonHelper.getAsJsonObject(json, "result"));
                return factory.create(id.getNamespace(), id.getPath(), pattern, key, ItemStackSpec.of(result),
                        net.minecraft.util.GsonHelper.getAsString(json, "copy_key", ""));
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
                ItemStackSpec result = ItemStackSpec.of(buffer.readItem());
                return factory.create(id.getNamespace(), id.getPath(), pattern, key, result, buffer.readUtf());
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
                buffer.writeUtf(recipe.copyFrom());
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
                ItemStack result = legacyResult(
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

    //? if <1.21.1 {
    /*/^*
     * A crafting result with its NBT: vanilla's itemStackFromJson reads item and count only, and the
     * component-era declarations arrive here with their components folded into a {@code tag} object by
     * legacy-recipes.gradle.kts.
     ^/
    private static ItemStack legacyResult(com.google.gson.JsonObject json) {
        ItemStack stack = net.minecraft.world.item.crafting.ShapedRecipe.itemStackFromJson(json);
        com.google.gson.JsonObject tag = json.has("tag") ? net.minecraft.util.GsonHelper.getAsJsonObject(json, "tag")
                : json.has("nbt") ? net.minecraft.util.GsonHelper.getAsJsonObject(json, "nbt") : null;
        if (tag != null) {
            try {
                stack.setTag(net.minecraft.nbt.TagParser.parseTag(tag.toString()));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new com.google.gson.JsonSyntaxException("Invalid result tag: " + tag, e);
            }
        }
        return stack;
    }
    *///?}
}
