package dev.golem.core.jei;

//? if jei {

import dev.golem.core.viewer.Drawable;
import dev.golem.core.viewer.Role;
import dev.golem.core.viewer.SlotLayout;
import dev.golem.core.viewer.ViewerCategory;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ViewerCategory} as a JEI category. The slots go straight to JEI's layout builder, the standard slot frame is
 * JEI's own, and the category's drawing is the category's, so a category written for every viewer draws in JEI exactly
 * as one written against JEI did.
 */
final class JeiCategory<T> extends AbstractRecipeCategory<T> {

    private final ViewerCategory<T> category;
    private final IGuiHelper helper;

    JeiCategory(IGuiHelper helper, ViewerCategory<T> category) {
        super(JeiViewer.type(category.type()).unwrap(), category.title(), helper.createDrawableItemStack(category.icon()),
                category.width(), category.height());
        this.category = category;
        this.helper = helper;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses) {
        category.setRecipe(new SlotLayout() {
            @Override
            public SlotLayout.Slot addSlot(Role role, int x, int y) {
                return new JeiSlot(builder.addSlot(role(role), x, y));
            }

            @Override
            public void addInvisible(Role role, List<ItemStack> stacks) {
                builder.addInvisibleIngredients(role(role)).addItemStacks(stacks);
            }
        }, recipe);
    }

    @Override
    public void draw(T recipe, IRecipeSlotsView slots,
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            double mouseX, double mouseY) {
        category.draw(recipe, graphics, mouseX, mouseY);
    }

    private static RecipeIngredientRole role(Role role) {
        return switch (role) {
            case INPUT -> RecipeIngredientRole.INPUT;
            case OUTPUT -> RecipeIngredientRole.OUTPUT;
            case CRAFTING_STATION -> Jei.CRAFTING_STATION;
        };
    }

    private final class JeiSlot implements SlotLayout.Slot {
        private final IRecipeSlotBuilder slot;

        JeiSlot(IRecipeSlotBuilder slot) {
            this.slot = slot;
        }

        @Override
        public SlotLayout.Slot addItemStacks(List<ItemStack> stacks) {
            slot.addItemStacks(stacks);
            return this;
        }

        @Override
        public SlotLayout.Slot setBackground(Drawable background, int dx, int dy) {
            if (background == Drawable.slot()) {
                if (dx == -1 && dy == -1) slot.setStandardSlotBackground();
                else slot.setBackground(helper.getSlotDrawable(), dx, dy);
            } else {
                slot.setBackground(wrap(background), dx, dy);
            }
            return this;
        }

        @Override
        public SlotLayout.Slot addTooltipCallback(SlotLayout.TooltipCallback callback) {
            slot.addRichTooltipCallback((view, tooltip) -> {
                List<Component> lines = new ArrayList<>();
                callback.addLines(view.getDisplayedItemStack().orElse(ItemStack.EMPTY), lines);
                for (Component line : lines) tooltip.add(line);
            });
            return this;
        }
    }

    private static IDrawable wrap(Drawable drawable) {
        return new IDrawable() {
            @Override
            public int getWidth() {
                return drawable.width();
            }

            @Override
            public int getHeight() {
                return drawable.height();
            }

            @Override
            public void draw(
                    //? if >=26.1 {
                    GuiGraphicsExtractor graphics,
                    //?} else {
                    /*GuiGraphics graphics,
                    *///?}
                    int x, int y) {
                drawable.draw(graphics, x, y);
            }
        };
    }
}
//?}
