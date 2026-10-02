package dev.golem.core.world;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

/** A smelting recipe whose item input and result can carry exact integer components. */
public final class ComponentSmeltingRecipe extends SmeltingRecipe {

    public static final RecipeSerializer<ComponentSmeltingRecipe> SERIALIZER =
            CustomRecipes.componentSmelting(ComponentSmeltingRecipe::new);

    private final ComponentIngredient componentIngredient;
    private final ItemStackSpec resultSpec;

    protected ComponentSmeltingRecipe(String namespace, String path, ComponentIngredient ingredient, ItemStackSpec result,
                                      float experience, int cookingTime) {
        //? if >=26.1 {
        super(new net.minecraft.world.item.crafting.Recipe.CommonInfo(true),
                new net.minecraft.world.item.crafting.AbstractCookingRecipe.CookingBookInfo(
                        net.minecraft.world.item.crafting.CookingBookCategory.MISC, ""),
                ingredient.displayIngredient(), result.template(), experience, cookingTime);
        //?} else if >=1.21.1 {
        /*super("", net.minecraft.world.item.crafting.CookingBookCategory.MISC,
                ingredient.displayIngredient(), result.create(), experience, cookingTime);
        *///?} else {
        /*super(new net.minecraft.resources.ResourceLocation(namespace, path), "",
                net.minecraft.world.item.crafting.CookingBookCategory.MISC,
                ingredient.displayIngredient(), result.create(), experience, cookingTime);
        *///?}
        this.componentIngredient = ingredient;
        this.resultSpec = result;
    }

    public ComponentIngredient componentIngredient() {
        return componentIngredient;
    }

    //? if >=26.1 {
    public net.minecraft.world.item.ItemStackTemplate resultTemplate() {
        return resultSpec.template();
    }
    //?} else {
    /*public ItemStack result() {
        return resultSpec.create();
    }
    *///?}

    //? if >=1.21.1 {
    @Override
    public boolean matches(net.minecraft.world.item.crafting.SingleRecipeInput input, Level level) {
        return componentIngredient.test(input.item());
    }
    //?} else {
    /*@Override
    public boolean matches(net.minecraft.world.Container input, Level level) {
        // A furnace passes its own three-slot container, so the size cannot be required to be 1; like vanilla's
        // SmeltingRecipe, only slot 0 is the input. (Requiring 1 made the recipe match nothing in a real furnace.)
        return input.getContainerSize() >= 1 && componentIngredient.test(input.getItem(0));
    }
    *///?}

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public RecipeSerializer getSerializer() {
        return SERIALIZER;
    }
}
