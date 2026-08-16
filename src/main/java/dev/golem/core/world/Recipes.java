package dev.golem.core.world;

import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

//? if >=1.21.1 {
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
//?} else {
/*import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.crafting.RecipeType;
*///?}

/** Recipe lookups that deliberately hide vanilla recipe input and holder types. */
public final class Recipes {

    private Recipes() {}

    /**
     * Finds the first matching smelting recipe and returns an independent result stack.
     *
     * <p>The lookup is server-side; a client level, empty input, or empty recipe output yields
     * {@link Optional#empty()}.
     */
    public static Optional<ItemStack> smeltingResult(Level level, ItemStack input) {
        if (input.isEmpty()) return Optional.empty();

        //? if >=26.1 {
        if (!(level instanceof ServerLevel serverLevel)) return Optional.empty();
        return serverLevel.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level)
                .map(holder -> holder.value().assemble(new SingleRecipeInput(input)))
                .filter(result -> !result.isEmpty())
                .map(ItemStack::copy);
        //?} elif >=1.21.1 {
        /*return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level)
                .map(holder -> holder.value().assemble(new SingleRecipeInput(input), level.registryAccess()))
                .filter(result -> !result.isEmpty())
                .map(ItemStack::copy);
        *///?} else {
        /*return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(input), level)
                .map(recipe -> recipe.assemble(new SimpleContainer(input), level.registryAccess()))
                .filter(result -> !result.isEmpty())
                .map(ItemStack::copy);
        *///?}
    }
}
