package dev.golem.core.jei;

//? if jei {

import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
// No DataComponentType import: the type is only ever inferred here (through GolemComponent.type()),
// and naming it would break 1.20.1, where net.minecraft.core.component does not exist at all.
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The parts of JEI's registration API that differ between its 19.x and 29.x lines, in one place.
 *
 * <p>Everything here exists so a mod's plugin reads the same on every target. Each method is the 29.x
 * spelling — the line this port is written against — implemented on 19.x in terms of whatever that line
 * calls the same thing. Compiled out entirely on a node that declares no JEI coordinates.
 *
 * @see JeiRecipeType for the type that appears in nearly every JEI signature
 */
public final class Jei {

    private Jei() {}

    /**
     * The role for the block that performs a recipe — the machine shown in a category's corner.
     *
     * <p>29.x names this {@code CRAFTING_STATION}. 19.x has no such constant and uses {@code CATALYST}
     * for the same thing; the rename came with no behavioural change.
     */
    //? if >=26.1 {
    public static final RecipeIngredientRole CRAFTING_STATION = RecipeIngredientRole.CRAFTING_STATION;
    //?} else {
    /*public static final RecipeIngredientRole CRAFTING_STATION = RecipeIngredientRole.CATALYST;
    *///?}

    /**
     * Declares the block(s) that perform a recipe type. 29.x calls this {@code addCraftingStation};
     * 19.x calls the same registration {@code addRecipeCatalyst}.
     */
    public static void addCraftingStation(IRecipeCatalystRegistration registration, JeiRecipeType<?> type,
                                          ItemStack... stations) {
        //? if >=26.1 {
        registration.addCraftingStation(type.unwrap(), stations);
        //?} else {
        /*// 19.x takes the item first and the types as varargs, and accepts an ItemLike rather than a
        // stack. Every catalyst here is a plain machine block, so the item alone carries all of it.
        for (ItemStack station : stations) registration.addRecipeCatalyst(station.getItem(), type.unwrap());
        *///?}
    }

    /**
     * The same, for one of JEI's own built-in types ({@code RecipeTypes.SMELTING} and friends), which
     * are already the native type and so cannot go through {@link JeiRecipeType}. Only the parameter's
     * spelling differs between the lines, so the call site reads identically.
     */
    public static void addCraftingStation(IRecipeCatalystRegistration registration,
            //? if >=26.1 {
            mezz.jei.api.recipe.types.IRecipeType<?> type,
            //?} else {
            /*mezz.jei.api.recipe.RecipeType<?> type,
            *///?}
            ItemStack... stations) {
        //? if >=26.1 {
        registration.addCraftingStation(type, stations);
        //?} else {
        /*for (ItemStack station : stations) registration.addRecipeCatalyst(station.getItem(), type);
        *///?}
    }

    /** Adds the recipes of one type. Identical on both lines once the type is unwrapped. */
    public static <T> void addRecipes(IRecipeRegistration registration, JeiRecipeType<T> type, List<T> recipes) {
        registration.addRecipes(type.unwrap(), recipes);
    }

    /**
     * Makes an item's JEI identity depend on a data component, so stacks differing only in that
     * component (a fluid icon's fluid, say) are treated as distinct ingredients.
     *
     * <p>29.x offers {@code registerFromDataComponentTypes} directly. 19.x predates it and takes a
     * subtype interpreter, so this supplies one that reads the same component — the component API
     * itself is unchanged between the two, which is what makes the reimplementation exact.
     */
    public static void registerFromDataComponentTypes(ISubtypeRegistration registration, Item item,
                                                      dev.golem.core.data.GolemComponent<?> component) {
        //? if >=26.1 {
        registration.registerFromDataComponentTypes(item, component.type());
        //?} else {
        /*registration.registerSubtypeInterpreter(item, (stack, context) -> {
            Object value = component.get(stack).orElse(null);
            return value == null ? "" : value.toString();
        });
        *///?}
    }
}
//?}
