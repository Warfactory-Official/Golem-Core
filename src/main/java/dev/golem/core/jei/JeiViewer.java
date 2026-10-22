package dev.golem.core.jei;

//? if jei {

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.golem.core.harness.HarnessQueryRegistry;
import dev.golem.core.viewer.Subtypes;
import dev.golem.core.viewer.TransferAction;
import dev.golem.core.viewer.ViewerCategory;
import dev.golem.core.viewer.ViewerPlugin;
import dev.golem.core.viewer.ViewerType;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Feeds a {@link ViewerPlugin} to JEI. A mod's {@code @JeiPlugin} calls one method here per JEI phase; each turns the
 * neutral declarations into the {@link Jei} calls for the node's JEI line, so the same declarations also feed REI
 * ({@code dev.golem.core.rei.ReiClientPlugin}).
 */
public final class JeiViewer {

    private static final Map<ViewerType<?>, JeiRecipeType<?>> TYPES = new ConcurrentHashMap<>();

    private JeiViewer() {}

    /** The JEI recipe type of a viewer category (one per category, created on first use). */
    @SuppressWarnings("unchecked")
    public static <T> JeiRecipeType<T> type(ViewerType<T> type) {
        return (JeiRecipeType<T>) TYPES.computeIfAbsent(type,
                t -> JeiRecipeType.create(t.namespace(), t.path(), t.recipeClass()));
    }

    public static void subtypes(ISubtypeRegistration registration, Subtypes.Declarations declarations) {
        declarations.register(new Subtypes() {
            @Override
            public void components(Item item, dev.golem.core.data.GolemComponent<?>... components) {
                Jei.registerFromDataComponentTypes(registration, item, components);
            }

            @Override
            public void interpreter(Item item, Function<ItemStack, String> interpreter) {
                Jei.registerSubtypeInterpreter(registration, item, interpreter);
            }
        });
    }

    public static void ingredients(IExtraIngredientRegistration registration, ViewerPlugin plugin) {
        plugin.ingredients(registration::addExtraItemStacks);
    }

    public static void categories(IRecipeCategoryRegistration registration, ViewerPlugin plugin) {
        IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
        plugin.categories(categories -> {
            for (ViewerCategory<?> category : categories) registration.addRecipeCategories(wrap(helper, category));
        });
    }

    private static <T> JeiCategory<T> wrap(IGuiHelper helper, ViewerCategory<T> category) {
        return new JeiCategory<>(helper, category);
    }

    public static void recipes(IRecipeRegistration registration, ViewerPlugin plugin) {
        plugin.recipes(new ViewerPlugin.Recipes() {
            @Override
            public <T> void add(ViewerType<T> type, List<T> recipes) {
                Jei.addRecipes(registration, type(type), recipes);
            }
        });
    }

    public static void stations(IRecipeCatalystRegistration registration, ViewerPlugin plugin) {
        plugin.stations(new ViewerPlugin.Stations() {
            @Override
            public void add(ViewerType<?> type, ItemStack... stations) {
                Jei.addCraftingStation(registration, type(type), stations);
            }

            @Override
            public void addSmelting(ItemStack... stations) {
                Jei.addCraftingStation(registration, RecipeTypes.SMELTING, stations);
            }
        });
    }

    public static void clickAreas(IGuiHandlerRegistration registration, ViewerPlugin plugin) {
        plugin.clickAreas(new ViewerPlugin.ClickAreas() {
            @Override
            public void add(Class<? extends AbstractContainerScreen<?>> screen, int x, int y, int width, int height,
                            ViewerType<?>... types) {
                for (ViewerType<?> type : types) Jei.addRecipeClickArea(registration, screen, x, y, width, height, type(type));
            }
        });
    }

    public static void transfers(IRecipeTransferRegistration registration, ViewerPlugin plugin) {
        plugin.transfers(new ViewerPlugin.Transfers() {
            @Override
            public <C extends AbstractContainerMenu, R> void add(Class<? extends C> menuClass, MenuType<C> menuType,
                    ViewerType<R> type, int recipeSlotStart, int recipeSlotCount,
                    int inventorySlotStart, int inventorySlotCount) {
                Jei.addRecipeTransferHandler(registration, menuClass, menuType, type(type), recipeSlotStart,
                        recipeSlotCount, inventorySlotStart, inventorySlotCount);
            }

            @Override
            public <C extends AbstractContainerMenu, R> void addSelective(Class<? extends C> menuClass,
                    MenuType<C> menuType, ViewerType<R> type, int[] recipeSlots, int inventorySlotStart,
                    int inventorySlotCount, Predicate<R> canHandle) {
                Jei.addSelectiveRecipeTransferHandler(registration, menuClass, menuType, type(type), recipeSlots,
                        inventorySlotStart, inventorySlotCount, canHandle);
            }

            @Override
            public <C extends AbstractContainerMenu, R> void addAction(Class<? extends C> menuClass,
                    MenuType<C> menuType, ViewerType<R> type, TransferAction<C, R> action) {
                Jei.addRecipeTransferAction(registration, menuClass, menuType, type(type), action);
            }
        });
    }

    public static void runtime(IJeiRuntime runtime, ViewerPlugin plugin) {
        registerHarnessQuery(runtime);
        plugin.runtime(new ViewerPlugin.Runtime() {
            @Override
            public String viewer() {
                return "JEI";
            }

            @Override
            public List<Map.Entry<ItemStack, ItemStack>> smeltingRecipes() {
                return Jei.smeltingRecipes(runtime);
            }

            @Override
            public <T> void addRecipes(ViewerType<T> type, List<T> recipes) {
                Jei.addRecipes(runtime, type(type), recipes);
            }
        });
        if (!Jei.showHiddenIngredients(runtime)) plugin.hidden(stacks -> Jei.removeItemStacks(runtime, stacks));
    }

    /**
     * The harness query {@code jei} (dev clients only): {@code op=open category=ns:path} opens JEI's recipe screen on
     * one category, as clicking its tab would, and answers the category's title and recipe count; without a category it lists every category with its title, recipe and catalyst counts.
     */
    private static void registerHarnessQuery(IJeiRuntime runtime) {
        HarnessQueryRegistry.register("jei", (mc, params) -> {
            JsonObject out = new JsonObject();
            if (params.has("focus")) {
                // R (output) or U (input) on the main-hand stack: recipes per category, as the recipe screen counts them
                ItemStack held = mc.player.getMainHandItem();
                boolean output = params.get("focus").getAsString().equals("output");
                var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(
                        output ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, held);
                JsonObject counts = new JsonObject();
                for (ViewerType<?> type : TYPES.keySet()) {
                    long count = runtime.getRecipeManager().createRecipeLookup(type(type).unwrap())
                            .limitFocus(List.of(focus)).get().count();
                    if (count > 0) counts.addProperty(type.id(), count);
                }
                out.addProperty("stack", held.toString());
                out.add("counts", counts);
                return out;
            }
            if (!params.has("category")) {
                JsonArray all = new JsonArray();
                for (ViewerType<?> type : TYPES.keySet()) {
                    var jeiType = type(type).unwrap();
                    JsonObject row = new JsonObject();
                    row.addProperty("id", type.id());
                    row.addProperty("title", runtime.getRecipeManager().getRecipeCategory(jeiType).getTitle().getString());
                    row.addProperty("recipes", runtime.getRecipeManager().createRecipeLookup(jeiType).get().count());
                    row.addProperty("catalysts", runtime.getRecipeManager().createRecipeCatalystLookup(jeiType).get().count());
                    all.add(row);
                }
                out.add("categories", all);
                return out;
            }
            String category = params.get("category").getAsString();
            for (ViewerType<?> type : TYPES.keySet()) {
                if (!type.id().equals(category)) continue;
                var jeiType = type(type).unwrap();
                out.addProperty("title", runtime.getRecipeManager().getRecipeCategory(jeiType).getTitle().getString());
                out.addProperty("recipes", runtime.getRecipeManager().createRecipeLookup(jeiType).get().count());
                runtime.getRecipesGui().showTypes(List.of(jeiType));
                out.addProperty("opened", true);
                return out;
            }
            out.addProperty("opened", false);
            return out;
        });
    }
}
//?}
