package dev.golem.core.viewer;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A mod's recipe-viewer integration, declared once and fed to every viewer: JEI through
 * {@code dev.golem.core.jei.JeiViewer} (called from the mod's {@code @JeiPlugin}), REI through
 * {@code dev.golem.core.rei.ReiClientPlugin} (subclassed by the mod's REI client plugin). The phases are JEI's plugin
 * phases; REI's adapter calls them from its own matching registries.
 *
 * <p>Client-only: it names screens. Subtypes, which REI also registers on a dedicated server, are separate
 * ({@link Subtypes.Declarations}).
 */
public interface ViewerPlugin {

    /** Stacks to list beside the registered items (JEI's extra ingredients, REI's entries). */
    default void ingredients(Ingredients registration) {}

    void categories(Categories registration);

    void recipes(Recipes registration);

    /** The blocks that run each category (JEI's crafting stations, REI's workstations). */
    default void stations(Stations registration) {}

    /** Screen regions that open categories when clicked. */
    default void clickAreas(ClickAreas registration) {}

    /** Menus a recipe's ingredients can be moved into (JEI's "+" transfer, REI's "+" button). */
    default void transfers(Transfers registration) {}

    /** Runs once the viewer has its recipes, for categories built from other recipes (the furnace's, say). */
    default void runtime(Runtime runtime) {}

    interface Ingredients {
        void addItemStacks(List<ItemStack> stacks);
    }

    interface Categories {
        void add(ViewerCategory<?>... categories);
    }

    interface Recipes {
        <T> void add(ViewerType<T> type, List<T> recipes);
    }

    interface Stations {
        void add(ViewerType<?> type, ItemStack... stations);

        /** A station for the viewer's own furnace (smelting) category. */
        void addSmelting(ItemStack... stations);
    }

    interface ClickAreas {
        /** A {@code width x height} region at {@code (x, y)} of the screen's GUI (relative to its left/top). */
        void add(Class<? extends AbstractContainerScreen<?>> screen, int x, int y, int width, int height,
                 ViewerType<?>... types);
    }

    interface Transfers {
        /** Recipe inputs go, in order, into {@code recipeSlotCount} menu slots from {@code recipeSlotStart}. */
        <C extends AbstractContainerMenu, R> void add(Class<? extends C> menuClass, MenuType<C> menuType,
                ViewerType<R> type, int recipeSlotStart, int recipeSlotCount,
                int inventorySlotStart, int inventorySlotCount);

        /** Recipe inputs go, in order, into the listed menu slots; recipes failing {@code canHandle} are not moved. */
        <C extends AbstractContainerMenu, R> void addSelective(Class<? extends C> menuClass, MenuType<C> menuType,
                ViewerType<R> type, int[] recipeSlots, int inventorySlotStart, int inventorySlotCount,
                Predicate<R> canHandle);
    }

    interface Runtime {
        /** The viewer's name ({@code "JEI"}, {@code "REI"}), for logs. */
        String viewer();

        /** Every furnace (smelting) recipe the viewer knows, one (input, result) pair per input item. */
        List<Map.Entry<ItemStack, ItemStack>> smeltingRecipes();

        <T> void addRecipes(ViewerType<T> type, List<T> recipes);
    }
}
