package dev.golem.core.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * A shapeless recipe over {@link ComponentIngredient}s: 1.7's {@code ShapelessOreRecipe} with its
 * metadata, NBT and {@code container<amount><fluid>} ingredients. Each ingredient consumes exactly one
 * stack, no stack may be left over, and each consumed stack leaves its ingredient's
 * {@link ComponentIngredient#remainderFor remainder} in the grid.
 */
public class ComponentShapelessRecipe extends CompatCustomRecipe {

    public static final RecipeSerializer<ComponentShapelessRecipe> SERIALIZER =
            CustomRecipes.componentShapeless(ComponentShapelessRecipe::new);

    private final List<ComponentIngredient> ingredients;
    private final ItemStackSpec result;

    protected ComponentShapelessRecipe(String namespace, String path, List<ComponentIngredient> ingredients,
                                       ItemStackSpec result) {
        super(namespace, path);
        if (ingredients.isEmpty() || ingredients.size() > 9) {
            throw new IllegalArgumentException("shapeless recipe needs 1..9 ingredients, got " + ingredients.size());
        }
        for (ComponentIngredient ingredient : ingredients) {
            if (ingredient.isEmpty()) throw new IllegalArgumentException("empty shapeless ingredient");
        }
        this.ingredients = List.copyOf(ingredients);
        this.result = result;
    }

    public final List<ComponentIngredient> ingredients() {
        return ingredients;
    }

    public final ItemStack result() {
        return result.create();
    }

    //? if >=26.1 {
    public final net.minecraft.world.item.ItemStackTemplate resultTemplate() {
        return result.template();
    }
    //?}

    @Override
    public boolean isSpecial() {
        return false;
    }

    @Override
    protected boolean matchesItems(List<ItemStack> input, Level level) {
        return assignment(input) != null;
    }

    @Override
    protected ItemStack assembleItems(List<ItemStack> input) {
        return assignment(input) != null ? finishResult(result.create()) : ItemStack.EMPTY;
    }

    /** Lets a mod derive state the result needs but a recipe file cannot carry (a fluid tint). */
    protected ItemStack finishResult(ItemStack assembled) {
        return assembled;
    }

    /**
     * For each input slot, the ingredient that consumes it (null for an empty slot), or null when the
     * stacks cannot be matched one-to-one against the ingredients.
     */
    private ComponentIngredient[] assignment(List<ItemStack> input) {
        List<Integer> occupied = new ArrayList<>();
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.get(slot);
            if (stack != null && !stack.isEmpty()) occupied.add(slot);
        }
        if (occupied.size() != ingredients.size()) return null;
        int[] owner = new int[ingredients.size()];
        Arrays.fill(owner, -1);
        for (int i = 0; i < occupied.size(); i++) {
            if (!augment(i, input, occupied, owner, new boolean[ingredients.size()])) return null;
        }
        ComponentIngredient[] result = new ComponentIngredient[input.size()];
        for (int ingredient = 0; ingredient < owner.length; ingredient++) {
            result[occupied.get(owner[ingredient])] = ingredients.get(ingredient);
        }
        return result;
    }

    /** Kuhn's augmenting path: a stack may take a free ingredient or displace one that can move. */
    private boolean augment(int stackIndex, List<ItemStack> input, List<Integer> occupied, int[] owner,
                            boolean[] visited) {
        ItemStack stack = input.get(occupied.get(stackIndex));
        for (int ingredient = 0; ingredient < ingredients.size(); ingredient++) {
            if (visited[ingredient] || !ingredients.get(ingredient).test(stack)) continue;
            visited[ingredient] = true;
            if (owner[ingredient] == -1 || augment(owner[ingredient], input, occupied, owner, visited)) {
                owner[ingredient] = stackIndex;
                return true;
            }
        }
        return false;
    }

    private NonNullList<ItemStack> remaining(List<ItemStack> input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        ComponentIngredient[] assigned = assignment(input);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.get(slot);
            if (stack.isEmpty()) continue;
            ItemStack remainder = assigned != null && assigned[slot] != null
                    ? assigned[slot].remainderFor(stack) : Compat.craftingRemainder(stack);
            if (!remainder.isEmpty()) remaining.set(slot, remainder);
        }
        return remaining;
    }

    //? if >=1.21.1 {
    @Override
    public NonNullList<ItemStack> getRemainingItems(net.minecraft.world.item.crafting.CraftingInput input) {
        return remaining(items(input));
    }
    //?} else {
    /*@Override
    public NonNullList<ItemStack> getRemainingItems(net.minecraft.world.inventory.CraftingContainer input) {
        List<ItemStack> stacks = new ArrayList<>(input.getContainerSize());
        for (int slot = 0; slot < input.getContainerSize(); slot++) stacks.add(input.getItem(slot));
        return remaining(stacks);
    }
    *///?}

    //? if >=26.1 {
    @Override
    public net.minecraft.world.item.crafting.PlacementInfo placementInfo() {
        return net.minecraft.world.item.crafting.PlacementInfo.create(
                ingredients.stream().map(ComponentIngredient::displayIngredient).toList());
    }

    @Override
    public List<net.minecraft.world.item.crafting.display.RecipeDisplay> display() {
        return List.of(new net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay(
                ingredients.stream().map(ingredient -> ingredient.displayIngredient().display()).toList(),
                new net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay(resultTemplate()),
                new net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }
    //?}

    //? if <26.1 {
    /*@Override
    public net.minecraft.core.NonNullList<Ingredient> getIngredients() {
        net.minecraft.core.NonNullList<Ingredient> copy = net.minecraft.core.NonNullList.create();
        for (ComponentIngredient ingredient : ingredients) copy.add(ingredient.displayIngredient());
        return copy;
    }
    *///?}

    //? if >=1.21.1 && <26.1 {
    /*@Override
    public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider registries) {
        return result.create();
    }
    *///?}

    //? if <1.21.1 {
    /*@Override
    public ItemStack getResultItem(net.minecraft.core.RegistryAccess registries) {
        return result.create();
    }
    *///?}

    @Override
    protected RecipeSerializer<?> serializer() {
        return SERIALIZER;
    }
}
