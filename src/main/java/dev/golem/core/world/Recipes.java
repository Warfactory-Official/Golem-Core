package dev.golem.core.world;

import dev.golem.core.Ids;
import java.util.Optional;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

//? if >=1.21.1 {
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.SingleRecipeInput;
//?} else {
/*import net.minecraft.world.SimpleContainer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.RecipeType;
*///?}

/** Recipe lookups that deliberately hide vanilla recipe input and holder types. */
public final class Recipes {

    private Recipes() {}

    /** Returns whether the level's active recipe manager contains the namespaced recipe id. */
    public static boolean hasRecipe(Level level, String namespace, String path) {
        var id = Ids.of(namespace, path);
        //? if >=26.1 {
        if (level.getServer() == null) return false;
        return level.getServer().getRecipeManager().getRecipes().stream()
                .anyMatch(recipe -> recipe.id().identifier().equals(id));
        //?} else {
        /*return level.getRecipeManager().byKey(id).isPresent();
        *///?}
    }

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

    /** Finds and assembles a shaped crafting recipe for the supplied rectangular input grid. */
    public static Optional<ItemStack> craftingResult(Level level, int width, int height,
                                                      List<ItemStack> inputs) {
        if (width <= 0 || height <= 0 || inputs.size() != width * height) return Optional.empty();

        //? if >=26.1 {
        if (!(level instanceof ServerLevel serverLevel)) return Optional.empty();
        CraftingInput craftingInput = CraftingInput.of(width, height, inputs);
        return serverLevel.recipeAccess().getRecipeFor(RecipeType.CRAFTING, craftingInput, level)
                .map(holder -> holder.value().assemble(craftingInput))
                .filter(result -> !result.isEmpty())
                .map(ItemStack::copy);
        //?} elif >=1.21.1 {
        /*CraftingInput craftingInput = CraftingInput.of(width, height, inputs);
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftingInput, level)
                .map(holder -> holder.value().assemble(craftingInput, level.registryAccess()))
                .filter(result -> !result.isEmpty())
                .map(ItemStack::copy);
        *///?} else {
        /*NonNullList<ItemStack> slots = NonNullList.withSize(width * height, ItemStack.EMPTY);
        for (int i = 0; i < inputs.size(); i++) slots.set(i, inputs.get(i));
        TransientCraftingContainer craftingInput = new TransientCraftingContainer(null, width, height, slots);
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftingInput, level)
                .map(recipe -> recipe.assemble(craftingInput, level.registryAccess()))
                .filter(result -> !result.isEmpty())
                .map(ItemStack::copy);
        *///?}
    }

    /** Finds a shaped crafting recipe and returns its per-slot crafting remainders. */
    public static Optional<List<ItemStack>> craftingRemainders(Level level, int width, int height,
                                                                List<ItemStack> inputs) {
        if (width <= 0 || height <= 0 || inputs.size() != width * height) return Optional.empty();

        //? if >=26.1 {
        if (!(level instanceof ServerLevel serverLevel)) return Optional.empty();
        CraftingInput craftingInput = CraftingInput.of(width, height, inputs);
        return serverLevel.recipeAccess().getRecipeFor(RecipeType.CRAFTING, craftingInput, level)
                .map(holder -> List.copyOf(holder.value().getRemainingItems(craftingInput)));
        //?} elif >=1.21.1 {
        /*CraftingInput craftingInput = CraftingInput.of(width, height, inputs);
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftingInput, level)
                .map(holder -> List.copyOf(holder.value().getRemainingItems(craftingInput)));
        *///?} else {
        /*NonNullList<ItemStack> slots = NonNullList.withSize(width * height, ItemStack.EMPTY);
        for (int i = 0; i < inputs.size(); i++) slots.set(i, inputs.get(i));
        TransientCraftingContainer craftingInput = new TransientCraftingContainer(null, width, height, slots);
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftingInput, level)
                .map(recipe -> List.copyOf(recipe.getRemainingItems(craftingInput)));
        *///?}
    }

    /**
     * One crafting recipe that matched a grid, held opaquely so a caller can keep a chosen recipe across
     * ticks and ask again whether a (possibly different) grid still satisfies it. The old autocrafter kept
     * a {@code List<IRecipe>} for exactly this; the vanilla recipe and holder types stay hidden here.
     */
    public record CraftingMatch(Object recipe, String id) {
        /** The recipe's registry id ({@code hbm:foo}): what a block entity saves to remember its choice - never a list index. */
        @Override public String id() { return id; }
    }

    /**
     * Every crafting recipe matching the supplied rectangular grid, in recipe-manager order. Server side
     * only: a client level answers an empty list.
     */
    public static List<CraftingMatch> craftingMatches(Level level, int width, int height, List<ItemStack> inputs) {
        if (width <= 0 || height <= 0 || inputs.size() != width * height) return List.of();
        List<CraftingMatch> matches = new java.util.ArrayList<>();
        //? if >=26.1 {
        if (!(level instanceof ServerLevel serverLevel)) return List.of();
        CraftingInput craftingInput = CraftingInput.of(width, height, inputs);
        for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : serverLevel.recipeAccess().getRecipes()) {
            if (holder.value().getType() != RecipeType.CRAFTING) continue;
            @SuppressWarnings("unchecked")
            net.minecraft.world.item.crafting.Recipe<CraftingInput> recipe =
                    (net.minecraft.world.item.crafting.Recipe<CraftingInput>) holder.value();
            if (recipe.matches(craftingInput, level)) matches.add(new CraftingMatch(recipe, holder.id().identifier().toString()));
        }
        //?} elif >=1.21.1 {
        /*if (level.isClientSide()) return List.of();
        CraftingInput craftingInput = CraftingInput.of(width, height, inputs);
        for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (holder.value().getType() != RecipeType.CRAFTING) continue;
            @SuppressWarnings("unchecked")
            net.minecraft.world.item.crafting.Recipe<CraftingInput> recipe =
                    (net.minecraft.world.item.crafting.Recipe<CraftingInput>) holder.value();
            if (recipe.matches(craftingInput, level)) matches.add(new CraftingMatch(recipe, holder.id().toString()));
        }
        *///?} else {
        /*if (level.isClientSide()) return List.of();
        NonNullList<ItemStack> slots = NonNullList.withSize(width * height, ItemStack.EMPTY);
        for (int i = 0; i < inputs.size(); i++) slots.set(i, inputs.get(i));
        TransientCraftingContainer craftingInput = new TransientCraftingContainer(null, width, height, slots);
        for (net.minecraft.world.item.crafting.Recipe<?> candidate : level.getRecipeManager().getRecipes()) {
            if (candidate.getType() != RecipeType.CRAFTING) continue;
            @SuppressWarnings("unchecked")
            net.minecraft.world.item.crafting.Recipe<net.minecraft.world.inventory.CraftingContainer> recipe =
                    (net.minecraft.world.item.crafting.Recipe<net.minecraft.world.inventory.CraftingContainer>) candidate;
            if (recipe.matches(craftingInput, level)) matches.add(new CraftingMatch(recipe, candidate.getId().toString()));
        }
        *///?}
        return matches;
    }

    /**
     * Assembles {@code match} against a grid, or {@link Optional#empty()} when the grid no longer
     * satisfies that recipe (or the level is a client).
     */
    public static Optional<ItemStack> craftingResult(Level level, CraftingMatch match, int width, int height,
                                                      List<ItemStack> inputs) {
        if (width <= 0 || height <= 0 || inputs.size() != width * height) return Optional.empty();
        //? if >=26.1 {
        if (!(level instanceof ServerLevel)) return Optional.empty();
        CraftingInput craftingInput = CraftingInput.of(width, height, inputs);
        @SuppressWarnings("unchecked")
        net.minecraft.world.item.crafting.Recipe<CraftingInput> recipe =
                (net.minecraft.world.item.crafting.Recipe<CraftingInput>) match.recipe();
        if (!recipe.matches(craftingInput, level)) return Optional.empty();
        ItemStack result = recipe.assemble(craftingInput);
        //?} elif >=1.21.1 {
        /*if (level.isClientSide()) return Optional.empty();
        CraftingInput craftingInput = CraftingInput.of(width, height, inputs);
        @SuppressWarnings("unchecked")
        net.minecraft.world.item.crafting.Recipe<CraftingInput> recipe =
                (net.minecraft.world.item.crafting.Recipe<CraftingInput>) match.recipe();
        if (!recipe.matches(craftingInput, level)) return Optional.empty();
        ItemStack result = recipe.assemble(craftingInput, level.registryAccess());
        *///?} else {
        /*if (level.isClientSide()) return Optional.empty();
        NonNullList<ItemStack> slots = NonNullList.withSize(width * height, ItemStack.EMPTY);
        for (int i = 0; i < inputs.size(); i++) slots.set(i, inputs.get(i));
        TransientCraftingContainer craftingInput = new TransientCraftingContainer(null, width, height, slots);
        @SuppressWarnings("unchecked")
        net.minecraft.world.item.crafting.Recipe<net.minecraft.world.inventory.CraftingContainer> recipe =
                (net.minecraft.world.item.crafting.Recipe<net.minecraft.world.inventory.CraftingContainer>) match.recipe();
        if (!recipe.matches(craftingInput, level)) return Optional.empty();
        ItemStack result = recipe.assemble(craftingInput, level.registryAccess());
        *///?}
        return result.isEmpty() ? Optional.empty() : Optional.of(result.copy());
    }
}
