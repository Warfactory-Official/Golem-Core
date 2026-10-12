package dev.golem.core.viewer;

/**
 * What a recipe slot holds. JEI's {@code RecipeIngredientRole} ({@code CRAFTING_STATION} is 19.x's {@code CATALYST});
 * REI marks inputs and outputs on its slot widget and keeps the station out of a display's ingredients.
 */
public enum Role {
    /** An ingredient: looking up its uses finds the recipe. */
    INPUT,
    /** A product: looking up its recipes finds the recipe. */
    OUTPUT,
    /** The block that runs the recipe, drawn in the category but not one of its ingredients. */
    CRAFTING_STATION
}
