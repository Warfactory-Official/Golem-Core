package dev.golem.core.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** A normal shapeless recipe that copies mutable state from one explicitly selected ingredient. */
public class ComponentCopyShapelessRecipe extends CompatCustomRecipe {

    public static final RecipeSerializer<ComponentCopyShapelessRecipe> SERIALIZER =
            CustomRecipes.componentCopyShapeless(ComponentCopyShapelessRecipe::new);

    private final List<Ingredient> ingredients;
    private final Ingredient copyFrom;
    private final ItemStackSpec result;
    private final int copyFromIngredient;

    protected ComponentCopyShapelessRecipe(String namespace, String path, List<Ingredient> ingredients,
                                           Ingredient copyFrom, ItemStackSpec result) {
        super(namespace, path);
        this.ingredients = List.copyOf(ingredients);
        this.copyFrom = copyFrom;
        this.result = result;

        int sourceIndex = -1;
        for (int i = 0; i < this.ingredients.size(); i++) {
            if (!this.ingredients.get(i).equals(copyFrom)) continue;
            if (sourceIndex != -1) {
                sourceIndex = -2;
                break;
            }
            sourceIndex = i;
        }
        this.copyFromIngredient = sourceIndex;
    }

    public final List<Ingredient> ingredients() {
        return ingredients;
    }

    public final Ingredient copyFrom() {
        return copyFrom;
    }

    public final ItemStack result() {
        return result.create();
    }

    //? if >=26.1 {
    /** The native recipe result representation on the template-based recipe API. */
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
        if (!validConfiguration()) return false;

        List<ItemStack> present = present(input);
        if (present.size() != ingredients.size()) return false;

        Integer source = sourceIndex(present);
        if (source == null) return false;

        boolean[] usedStacks = new boolean[present.size()];
        boolean[] usedIngredients = new boolean[ingredients.size()];
        usedStacks[source] = true;
        usedIngredients[copyFromIngredient] = true;
        return matchRemaining(present, usedStacks, usedIngredients);
    }

    @Override
    protected ItemStack assembleItems(List<ItemStack> input) {
        if (!matchesItems(input, null)) return ItemStack.EMPTY;

        List<ItemStack> present = present(input);
        ItemStack source = present.get(sourceIndex(present));
        ItemStack assembled = result.create();
        ItemStacks.copyCustomState(source, assembled);
        return finishResult(assembled);
    }

    /** Lets a feature remove state that is valid on the source but invalid on its output item. */
    protected ItemStack finishResult(ItemStack assembled) {
        return assembled;
    }

    private boolean validConfiguration() {
        return !ingredients.isEmpty() && !copyFrom.isEmpty() && copyFromIngredient >= 0;
    }

    private Integer sourceIndex(List<ItemStack> present) {
        Integer source = null;
        for (int i = 0; i < present.size(); i++) {
            if (!copyFrom.test(present.get(i))) continue;
            if (source != null) return null;
            source = i;
        }
        return source;
    }

    private boolean matchRemaining(List<ItemStack> present, boolean[] usedStacks, boolean[] usedIngredients) {
        int nextIngredient = -1;
        int fewestChoices = Integer.MAX_VALUE;
        for (int i = 0; i < ingredients.size(); i++) {
            if (usedIngredients[i]) continue;
            int choices = 0;
            for (int j = 0; j < present.size(); j++) {
                if (!usedStacks[j] && ingredients.get(i).test(present.get(j))) choices++;
            }
            if (choices == 0) return false;
            if (choices < fewestChoices) {
                fewestChoices = choices;
                nextIngredient = i;
            }
        }
        if (nextIngredient == -1) return true;

        usedIngredients[nextIngredient] = true;
        for (int i = 0; i < present.size(); i++) {
            if (usedStacks[i] || !ingredients.get(nextIngredient).test(present.get(i))) continue;
            usedStacks[i] = true;
            if (matchRemaining(present, usedStacks, usedIngredients)) return true;
            usedStacks[i] = false;
        }
        usedIngredients[nextIngredient] = false;
        return false;
    }

    private static List<ItemStack> present(List<ItemStack> input) {
        List<ItemStack> present = new ArrayList<>(input.size());
        for (ItemStack stack : input) if (stack != null && !stack.isEmpty()) present.add(stack);
        return present;
    }

    //? if >=26.1 {
    @Override
    public net.minecraft.world.item.crafting.PlacementInfo placementInfo() {
        return net.minecraft.world.item.crafting.PlacementInfo.create(ingredients);
    }

    @Override
    public List<net.minecraft.world.item.crafting.display.RecipeDisplay> display() {
        return List.of(new net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay(
                ingredients.stream().map(Ingredient::display).toList(),
                new net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay(
                        resultTemplate()),
                new net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }
    //?}

    //? if <26.1 {
    /*@Override
    public net.minecraft.core.NonNullList<Ingredient> getIngredients() {
        net.minecraft.core.NonNullList<Ingredient> copy = net.minecraft.core.NonNullList.create();
        copy.addAll(ingredients);
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
