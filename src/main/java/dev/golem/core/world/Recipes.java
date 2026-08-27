package dev.golem.core.world;

import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

//? if >=1.21.1 {
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import java.util.List;
//?} else {
/*import net.minecraft.world.SimpleContainer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.TransientCraftingContainer;
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

    /**
     * Finds the first matching one-slot crafting recipe and returns an independent result stack.
     * This is the recipe lookup needed by machines that reproduce a one-item crafting conversion
     * without depending on a loader's recipe-manager API.
     */
    public static Optional<ItemStack> craftingResult(Level level, ItemStack input) {
        if (input.isEmpty()) return Optional.empty();

        //? if >=26.1 {
        if (!(level instanceof ServerLevel serverLevel)) return Optional.empty();
        CraftingInput craftingInput = CraftingInput.of(1, 1, List.of(input));
        return serverLevel.recipeAccess().getRecipeFor(RecipeType.CRAFTING, craftingInput, level)
                .map(holder -> holder.value().assemble(craftingInput))
                .filter(result -> !result.isEmpty())
                .map(ItemStack::copy);
        //?} elif >=1.21.1 {
        /*CraftingInput craftingInput = CraftingInput.of(1, 1, List.of(input));
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftingInput, level)
                .map(holder -> holder.value().assemble(craftingInput, level.registryAccess()))
                .filter(result -> !result.isEmpty())
                .map(ItemStack::copy);
        *///?} else {
        /*TransientCraftingContainer craftingInput = new TransientCraftingContainer(null, 1, 1,
                NonNullList.of(ItemStack.EMPTY, input));
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftingInput, level)
                .map(recipe -> recipe.assemble(craftingInput, level.registryAccess()))
                .filter(result -> !result.isEmpty())
                .map(ItemStack::copy);
        *///?}
    }
}
