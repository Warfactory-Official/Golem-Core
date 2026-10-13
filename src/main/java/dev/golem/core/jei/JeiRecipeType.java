package dev.golem.core.jei;

//? if jei {

//? if >=26.1 {
import mezz.jei.api.recipe.types.IRecipeType;
//?} else {
/*import mezz.jei.api.recipe.RecipeType;
*///?}

/**
 * A JEI recipe type, spelled the same way on every JEI line.
 *
 * <p>29.x has the interface {@code mezz.jei.api.recipe.types.IRecipeType}; 15.x and 19.x have the final
 * class {@code mezz.jei.api.recipe.RecipeType}. It appears in the signature of nearly every category and
 * handler, so it would fork the whole integration.
 *
 * <p>A Stonecutter replacement cannot fix this: the older simple name {@code RecipeType} is also a vanilla
 * type ({@code net.minecraft.world.item.crafting.RecipeType}), so the mapping would not be reversible, and
 * Java has no type aliases. So this is a thin value wrapper: consumers name <em>this</em> type and call
 * {@link #unwrap()} where JEI's own API needs the real one. Those call sites are identical source on
 * both lines.
 *
 * @param <T> the recipe class this type identifies
 */
public final class JeiRecipeType<T> {

    //? if >=26.1 {
    private final IRecipeType<T> delegate;
    //?} else {
    /*private final RecipeType<T> delegate;
    *///?}

    //? if >=26.1 {
    private JeiRecipeType(IRecipeType<T> delegate) {
    //?} else {
    /*private JeiRecipeType(RecipeType<T> delegate) {
    *///?}
        this.delegate = delegate;
    }

    /**
     * Creates a type identified by {@code namespace:path} and carrying recipes of {@code recipeClass}.
     *
     * <p>Identity is {@code (id, class)} on both lines, so two categories whose recipes share a Java
     * class stay distinct while their paths differ. The id comes in two pieces because the id type is
     * version-split (Identifier / ResourceLocation) and 19.x's factory wants the pieces.
     */
    public static <T> JeiRecipeType<T> create(String namespace, String path, Class<T> recipeClass) {
        //? if >=26.1 {
        return new JeiRecipeType<>(IRecipeType.create(dev.golem.core.Ids.of(namespace, path), recipeClass));
        //?} else {
        /*return new JeiRecipeType<>(RecipeType.create(namespace, path, recipeClass));
        *///?}
    }

    /** The underlying JEI type, for the API calls that take it directly. */
    //? if >=26.1 {
    public IRecipeType<T> unwrap() {
    //?} else {
    /*public RecipeType<T> unwrap() {
    *///?}
        return delegate;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JeiRecipeType<?> that && delegate.equals(that.delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
//?}
