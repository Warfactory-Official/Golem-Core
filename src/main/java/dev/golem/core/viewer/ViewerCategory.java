package dev.golem.core.viewer;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A recipe category, written once for every recipe viewer: JEI's {@code AbstractRecipeCategory} without JEI's types.
 * The JEI adapter wraps it in a JEI category, the REI adapter in a REI display category, and both call
 * {@link #setRecipe} for the slots and {@link #draw} for everything else, in the category's own coordinates
 * ({@code 0..width, 0..height}).
 *
 * @param <T> the recipe class it shows
 */
public abstract class ViewerCategory<T> {

    private final ViewerType<T> type;
    private final Component title;
    private final ItemStack icon;
    private final int width;
    private final int height;

    protected ViewerCategory(ViewerType<T> type, Component title, ItemStack icon, int width, int height) {
        this.type = type;
        this.title = title;
        this.icon = icon;
        this.width = width;
        this.height = height;
    }

    public final ViewerType<T> type() {
        return type;
    }

    public final Component title() {
        return title;
    }

    public final ItemStack icon() {
        return icon;
    }

    public final int width() {
        return width;
    }

    public final int height() {
        return height;
    }

    /** Places one recipe's slots. */
    public abstract void setRecipe(SlotLayout builder, T recipe);

    /** Draws one recipe's background and extras; the mouse is in the category's coordinates. */
    public void draw(T recipe,
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            double mouseX, double mouseY) {
    }
}
