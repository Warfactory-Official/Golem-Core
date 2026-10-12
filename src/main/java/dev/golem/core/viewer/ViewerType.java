package dev.golem.core.viewer;

import java.util.Objects;

/**
 * A recipe category's identity, spelled the same way for every recipe viewer.
 *
 * <p>JEI calls this a recipe type ({@code IRecipeType} on 29.x, {@code RecipeType} on 15.x/19.x) and REI a category
 * identifier; both are a namespaced id plus, for JEI, the recipe class. A mod names its categories once with this type
 * and each viewer adapter ({@code dev.golem.core.jei.JeiViewer}, {@code dev.golem.core.rei.ReiClientPlugin}) maps it to
 * its own spelling. Identity is {@code (namespace, path, class)}, as JEI's: two categories whose recipes share a class
 * stay distinct as long as their paths differ.
 *
 * @param <T> the recipe class this category shows
 */
public final class ViewerType<T> {

    private final String namespace;
    private final String path;
    private final Class<T> recipeClass;

    private ViewerType(String namespace, String path, Class<T> recipeClass) {
        this.namespace = namespace;
        this.path = path;
        this.recipeClass = recipeClass;
    }

    /** A category identified by {@code namespace:path} that shows recipes of {@code recipeClass}. */
    public static <T> ViewerType<T> create(String namespace, String path, Class<T> recipeClass) {
        return new ViewerType<>(namespace, path, recipeClass);
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return path;
    }

    public Class<T> recipeClass() {
        return recipeClass;
    }

    /** {@code namespace:path}. */
    public String id() {
        return namespace + ":" + path;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ViewerType<?> that && namespace.equals(that.namespace) && path.equals(that.path)
                && recipeClass == that.recipeClass;
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path, recipeClass);
    }

    @Override
    public String toString() {
        return id();
    }
}
