package dev.golem.core.rei;

//? if rei {

import dev.golem.core.Ids;
import dev.golem.core.viewer.Subtypes;
import dev.golem.core.viewer.TransferAction;
import dev.golem.core.viewer.ViewerCategory;
import dev.golem.core.viewer.ViewerPlugin;
import dev.golem.core.viewer.ViewerType;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.entry.filtering.base.BasicFilteringRule;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.client.registry.transfer.simple.SimpleTransferHandler;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Feeds a {@link ViewerPlugin} to REI: a mod's REI client plugin subclasses this once (loader glue:
 * {@code @REIPluginClient} on Forge-likes, the {@code rei_client} entrypoint on Fabric) and passes the same declarations
 * its JEI plugin passes to {@code dev.golem.core.jei.JeiViewer}.
 *
 * <ul>
 *   <li>categories and stations: {@link ReiCategory} per category, its stations as REI workstations; a smelting station
 *       goes to REI's own furnace category;</li>
 *   <li>recipes: a {@link ReiDisplay} per recipe;</li>
 *   <li>click areas: REI container click areas, relative to the screen's left/top as JEI's are;</li>
 *   <li>transfers: REI simple transfer handlers over the same menu slots, restricted to the category's displays;</li>
 *   <li>ingredients: REI entries; hidden stacks: REI's basic entry filtering;</li>
 *   <li>runtime: the categories built from other recipes (the furnace's) are REI display generators that run the
 *       plugin's runtime the first time they are asked, once REI's furnace displays exist. A display generator rather
 *       than a reload hook because on 26.1 REI's furnace displays arrive from the server after the client reload.</li>
 * </ul>
 */
public abstract class ReiClientPlugin implements REIClientPlugin {

    private final ViewerPlugin plugin;
    private final Subtypes.Declarations subtypes;
    private final Map<ViewerType<?>, ViewerCategory<?>> categories = new LinkedHashMap<>();

    /** The runtime's displays per category, computed once REI knows furnace recipes; reset by each reload. */
    private Map<CategoryIdentifier<?>, List<Display>> runtimeDisplays;
    private int runtimeSmeltingCount = -1;

    protected ReiClientPlugin(ViewerPlugin plugin, Subtypes.Declarations subtypes) {
        this.plugin = plugin;
        this.subtypes = subtypes;
        PLUGINS.add(this);
        registerHarnessQuery();
    }

    private static final List<ReiClientPlugin> PLUGINS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean harnessQuery;

    /**
     * The harness query {@code rei} (dev clients only; a no-op otherwise): {@code op=status} counts each REI plugin's
     * categories and their displays as REI holds them (the runtime categories included), and lists the ids;
     * {@code op=open category=ns:path} opens REI's recipe screen on one category, as clicking its tab would.
     */
    private static synchronized void registerHarnessQuery() {
        if (harnessQuery) return;
        harnessQuery = true;
        dev.golem.core.harness.HarnessQueryRegistry.register("rei", (mc, params) -> {
            com.google.gson.JsonObject out = new com.google.gson.JsonObject();
            String op = params.has("op") ? params.get("op").getAsString() : "status";
            if (op.equals("open")) {
                String category = params.get("category").getAsString();
                int colon = category.indexOf(':');
                boolean opened = ViewSearchBuilder.builder()
                        .addCategory(CategoryIdentifier.of(category.substring(0, colon), category.substring(colon + 1)))
                        .open();
                out.addProperty("opened", opened);
                return out;
            }
            if (op.equals("transfer")) {
                // REI's "+" on the first display of the category whose inputs hold the item, into the open container
                // screen: every registered transfer handler in turn, as REI's own button does.
                String category = params.get("category").getAsString();
                int colon = category.indexOf(':');
                CategoryIdentifier<Display> id = CategoryIdentifier.<Display>of(category.substring(0, colon), category.substring(colon + 1));
                EntryStack<ItemStack> wanted = EntryStacks.of(BuiltInRegistries.ITEM.getValue(Ids.parse(params.get("item").getAsString())));
                Display display = null;
                for (Display candidate : DisplayRegistry.getInstance().get(id)) {
                    for (EntryIngredient input : candidate.getInputEntries()) {
                        if (EntryIngredients.testFuzzy(input, wanted)) { display = candidate; break; }
                    }
                    if (display != null) break;
                }
                if (display == null || !(mc.screen instanceof AbstractContainerScreen<?> screen)) {
                    out.addProperty("error", display == null ? "no display" : "no container screen: " + mc.screen);
                    return out;
                }
                String result = "not applicable";
                for (TransferHandler handler : me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry.getInstance()) {
                    TransferHandler.Context context = TransferHandler.Context.create(true, false, screen, display);
                    if (!handler.checkApplicable(context).isApplicable()) continue;
                    TransferHandler.Result r = handler.handle(context);
                    if (!r.isApplicable()) continue;
                    result = r.isSuccessful() ? "successful by " + handler.getClass().getName()
                            : "failed: " + (r.getError() == null ? "?" : r.getError().getString());
                    break;
                }
                out.addProperty("result", result);
                return out;
            }
            if (op.equals("uses")) {
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(Ids.parse(params.get("item").getAsString()));
                out.addProperty("opened", ViewSearchBuilder.builder().addUsagesFor(EntryStacks.of(item)).open());
                return out;
            }
            int categories = 0, displays = 0;
            com.google.gson.JsonArray ids = new com.google.gson.JsonArray();
            for (ReiClientPlugin plugin : PLUGINS) {
                for (ViewerType<?> type : plugin.categories.keySet()) {
                    CategoryIdentifier<Display> id = Rei.id(type).cast();
                    if (me.shedaniel.rei.api.client.registry.category.CategoryRegistry.getInstance().tryGet(id).isEmpty()) continue;
                    categories++;
                    int count = DisplayRegistry.getInstance().get(id).size()
                            + plugin.runtimeDisplays().getOrDefault(id, List.of()).size();
                    displays += count;
                    ids.add(type.id() + "=" + count);
                }
            }
            if (params.has("entries")) {
                // how many list entries REI keeps for one item: a subtype-registered item (a fluid icon, say) keeps
                // one per subtype, any other item collapses to one
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(Ids.parse(params.get("entries").getAsString()));
                out.addProperty("entries", me.shedaniel.rei.api.client.registry.entry.EntryRegistry.getInstance().getEntryStacks()
                        .filter(entry -> entry.getValue() instanceof ItemStack stack && stack.is(item)).count());
            }
            out.addProperty("categories", categories);
            out.addProperty("displays", displays);
            out.add("ids", ids);
            return out;
        });
    }

    //? if <26.1 {
    /*@Override
    public void registerItemComparators(me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry registry) {
        Rei.comparators(registry, subtypes);
    }
    *///?}

    @Override
    public void registerCategories(CategoryRegistry registry) {
        categories.clear();
        plugin.categories(list -> {
            for (ViewerCategory<?> category : list) {
                categories.put(category.type(), category);
                registry.add(wrap(category));
            }
        });
        plugin.stations(new ViewerPlugin.Stations() {
            @Override
            public void add(ViewerType<?> type, ItemStack... stations) {
                registry.addWorkstations(Rei.id(type), entries(stations));
            }

            @Override
            public void addSmelting(ItemStack... stations) {
                registry.addWorkstations(Rei.SMELTING, entries(stations));
            }
        });
    }

    private static <T> ReiCategory<T> wrap(ViewerCategory<T> category) {
        return new ReiCategory<>(category, Rei.id(category.type()));
    }

    private static EntryStack<?>[] entries(ItemStack... stacks) {
        EntryStack<?>[] entries = new EntryStack<?>[stacks.length];
        for (int i = 0; i < stacks.length; i++) entries[i] = EntryStacks.of(stacks[i]);
        return entries;
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        plugin.recipes(new ViewerPlugin.Recipes() {
            @Override
            public <T> void add(ViewerType<T> type, List<T> recipes) {
                ViewerCategory<T> category = category(type);
                if (category == null) return;
                for (T recipe : recipes) registry.add(new ReiDisplay<>(category, Rei.id(type), recipe));
            }
        });
        runtimeDisplays = null;
        runtimeSmeltingCount = -1;
        for (ViewerType<?> type : categories.keySet()) {
            CategoryIdentifier<Display> id = Rei.id(type).cast();
            registry.registerDisplayGenerator(id, new RuntimeGenerator(id));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ViewerCategory<T> category(ViewerType<T> type) {
        return (ViewerCategory<T>) categories.get(type);
    }

    /** Answers REI's lookups for one category from the plugin's runtime displays. */
    private final class RuntimeGenerator implements DynamicDisplayGenerator<Display> {
        private final CategoryIdentifier<?> id;

        RuntimeGenerator(CategoryIdentifier<?> id) {
            this.id = id;
        }

        private List<Display> displays() {
            return runtimeDisplays().getOrDefault(id, List.of());
        }

        @Override
        public Optional<List<Display>> getRecipeFor(EntryStack<?> entry) {
            return matching(entry, false);
        }

        @Override
        public Optional<List<Display>> getUsageFor(EntryStack<?> entry) {
            return matching(entry, true);
        }

        private Optional<List<Display>> matching(EntryStack<?> entry, boolean usage) {
            List<Display> out = new ArrayList<>();
            for (Display display : displays()) {
                for (EntryIngredient ingredient : usage ? display.getInputEntries() : display.getOutputEntries()) {
                    if (EntryIngredients.testFuzzy(ingredient, entry)) {
                        out.add(display);
                        break;
                    }
                }
            }
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        }

        @Override
        public Optional<List<Display>> generate(ViewSearchBuilder builder) {
            List<Display> displays = displays();
            return displays.isEmpty() || !builder.getCategories().contains(id) ? Optional.empty() : Optional.of(displays);
        }
    }

    private synchronized Map<CategoryIdentifier<?>, List<Display>> runtimeDisplays() {
        List<Display> smelting = DisplayRegistry.getInstance().get(Rei.SMELTING.<Display>cast());
        if (runtimeDisplays != null && smelting.size() == runtimeSmeltingCount) return runtimeDisplays;
        Map<CategoryIdentifier<?>, List<Display>> displays = new LinkedHashMap<>();
        if (!smelting.isEmpty()) {
            List<Map.Entry<ItemStack, ItemStack>> pairs = smeltingPairs(smelting);
            plugin.runtime(new ViewerPlugin.Runtime() {
                @Override
                public String viewer() {
                    return "REI";
                }

                @Override
                public List<Map.Entry<ItemStack, ItemStack>> smeltingRecipes() {
                    return pairs;
                }

                @Override
                public <T> void addRecipes(ViewerType<T> type, List<T> recipes) {
                    ViewerCategory<T> category = category(type);
                    if (category == null) return;
                    List<Display> list = displays.computeIfAbsent(Rei.id(type), k -> new ArrayList<>());
                    for (T recipe : recipes) list.add(new ReiDisplay<>(category, Rei.id(type), recipe));
                }
            });
        }
        runtimeDisplays = displays;
        runtimeSmeltingCount = smelting.size();
        return displays;
    }

    /** REI's furnace displays as (input, result) pairs, one per input item, as JEI's lookup gives them. */
    private static List<Map.Entry<ItemStack, ItemStack>> smeltingPairs(List<Display> smelting) {
        List<Map.Entry<ItemStack, ItemStack>> pairs = new ArrayList<>();
        for (Display display : smelting) {
            if (display.getInputEntries().isEmpty() || display.getOutputEntries().isEmpty()) continue;
            ItemStack result = firstItem(display.getOutputEntries().get(0));
            if (result.isEmpty()) continue;
            for (EntryStack<?> input : display.getInputEntries().get(0)) {
                if (input.getValue() instanceof ItemStack stack && !stack.isEmpty()) {
                    pairs.add(Map.entry(stack.copyWithCount(1), result.copy()));
                }
            }
        }
        return pairs;
    }

    private static ItemStack firstItem(EntryIngredient ingredient) {
        for (EntryStack<?> entry : ingredient) {
            if (entry.getValue() instanceof ItemStack stack && !stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        plugin.clickAreas(new ViewerPlugin.ClickAreas() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public void add(Class<? extends AbstractContainerScreen<?>> screen, int x, int y, int width, int height,
                            ViewerType<?>... types) {
                CategoryIdentifier<?>[] ids = new CategoryIdentifier<?>[types.length];
                for (int i = 0; i < types.length; i++) ids[i] = Rei.id(types[i]);
                registry.registerContainerClickArea(new Rectangle(x, y, width, height), (Class) screen, ids);
            }
        });
    }

    @Override
    public void registerEntries(EntryRegistry registry) {
        plugin.ingredients(stacks -> {
            List<EntryStack<?>> entries = new ArrayList<>(stacks.size());
            for (ItemStack stack : stacks) entries.add(EntryStacks.of(stack));
            registry.addEntries(entries);
        });
    }

    @Override
    public void registerBasicEntryFiltering(BasicFilteringRule<?> rule) {
        plugin.hidden(stacks -> {
            List<EntryStack<?>> entries = new ArrayList<>(stacks.size());
            for (ItemStack stack : stacks) entries.add(EntryStacks.of(stack));
            rule.hide(entries);
        });
    }

    @Override
    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        plugin.transfers(new ViewerPlugin.Transfers() {
            @Override
            public <C extends AbstractContainerMenu, R> void add(Class<? extends C> menuClass, MenuType<C> menuType,
                    ViewerType<R> type, int recipeSlotStart, int recipeSlotCount,
                    int inventorySlotStart, int inventorySlotCount) {
                int[] slots = new int[recipeSlotCount];
                for (int i = 0; i < recipeSlotCount; i++) slots[i] = recipeSlotStart + i;
                addSelective(menuClass, menuType, type, slots, inventorySlotStart, inventorySlotCount, recipe -> true);
            }

            @Override
            public <C extends AbstractContainerMenu, R> void addSelective(Class<? extends C> menuClass,
                    MenuType<C> menuType, ViewerType<R> type, int[] recipeSlots, int inventorySlotStart,
                    int inventorySlotCount, Predicate<R> canHandle) {
                registry.register(new Transfer<>(menuClass, Rei.id(type), recipeSlots, inventorySlotStart,
                        inventorySlotCount, canHandle));
            }

            @Override
            public <C extends AbstractContainerMenu, R> void addAction(Class<? extends C> menuClass,
                    MenuType<C> menuType, ViewerType<R> type, TransferAction<C, R> action) {
                registry.register(new ActionTransfer<>(menuClass, Rei.id(type), action));
            }
        });
    }

    /** A {@link TransferAction} as a REI transfer handler; REI returns to the container screen on success. */
    private static final class ActionTransfer<C extends AbstractContainerMenu, R> implements TransferHandler {
        private final Class<?> menuClass;
        private final CategoryIdentifier<?> id;
        private final TransferAction<C, R> action;

        ActionTransfer(Class<?> menuClass, CategoryIdentifier<?> id, TransferAction<C, R> action) {
            this.menuClass = menuClass;
            this.id = id;
            this.action = action;
        }

        private boolean applies(TransferHandler.Context context) {
            AbstractContainerMenu menu = context.getMenu();
            return menu != null && menuClass.isInstance(menu) && context.getDisplay() instanceof ReiDisplay<?> display
                    && id.equals(display.getCategoryIdentifier());
        }

        @Override
        @SuppressWarnings("unchecked")
        public TransferHandler.Result handle(TransferHandler.Context context) {
            if (!applies(context)) return TransferHandler.Result.createNotApplicable();
            C menu = (C) context.getMenu();
            R recipe = (R) ((ReiDisplay<?>) context.getDisplay()).recipe();
            Component why = action.unavailable(menu, recipe);
            if (why != null) return TransferHandler.Result.createFailed(why);
            if (context.isActuallyCrafting()) TransferAction.runWhenBack(menu, recipe, action);
            return TransferHandler.Result.createSuccessful().blocksFurtherHandling();
        }
    }

    /** JEI's slot-list transfer as a REI simple transfer handler, for one category's displays in one menu class. */
    private static final class Transfer<R> implements SimpleTransferHandler {
        private final Class<?> menuClass;
        private final CategoryIdentifier<?> id;
        private final int[] recipeSlots;
        private final int inventoryStart;
        private final int inventoryCount;
        private final Predicate<R> canHandle;

        Transfer(Class<?> menuClass, CategoryIdentifier<?> id, int[] recipeSlots, int inventoryStart, int inventoryCount,
                 Predicate<R> canHandle) {
            this.menuClass = menuClass;
            this.id = id;
            this.recipeSlots = recipeSlots;
            this.inventoryStart = inventoryStart;
            this.inventoryCount = inventoryCount;
            this.canHandle = canHandle;
        }

        @SuppressWarnings("unchecked")
        private boolean applies(TransferHandler.Context context) {
            AbstractContainerMenu menu = context.getMenu();
            return menu != null && menuClass.isInstance(menu)
                    && context.getDisplay() instanceof ReiDisplay<?> display
                    && id.equals(display.getCategoryIdentifier())
                    && canHandle.test((R) display.recipe());
        }

        @Override
        public TransferHandler.ApplicabilityResult checkApplicable(TransferHandler.Context context) {
            return applies(context) ? TransferHandler.ApplicabilityResult.createApplicable()
                    : TransferHandler.ApplicabilityResult.createNotApplicable();
        }

        @Override
        public TransferHandler.Result handle(TransferHandler.Context context) {
            if (!applies(context)) return TransferHandler.Result.createNotApplicable();
            return SimpleTransferHandler.super.handle(context);
        }

        @Override
        public Iterable<SlotAccessor> getInputSlots(TransferHandler.Context context) {
            List<SlotAccessor> slots = new ArrayList<>(recipeSlots.length);
            for (int index : recipeSlots) slots.add(new MenuSlotAccessor(context.getMenu().getSlot(index)));
            return slots;
        }

        @Override
        public Iterable<SlotAccessor> getInventorySlots(TransferHandler.Context context) {
            List<SlotAccessor> slots = new ArrayList<>(inventoryCount);
            for (int i = inventoryStart; i < inventoryStart + inventoryCount; i++) {
                slots.add(SlotAccessor.fromSlot(context.getMenu().getSlot(i)));
            }
            return slots;
        }
    }
}
//?}
