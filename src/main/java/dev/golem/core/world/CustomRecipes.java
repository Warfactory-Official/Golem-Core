package dev.golem.core.world;

import java.util.function.Supplier;
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
}
