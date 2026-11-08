package dev.golem.core.emi;

//? if emi {
/*import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.golem.core.Ids;
import dev.golem.core.data.GolemComponent;
import dev.golem.core.harness.HarnessQueryRegistry;
import dev.golem.core.viewer.Subtypes;
import dev.golem.core.viewer.TransferAction;
import dev.golem.core.viewer.ViewerCategory;
import dev.golem.core.viewer.ViewerPlugin;
import dev.golem.core.viewer.ViewerType;
import dev.golem.core.world.Recipes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/^*
 * Feeds a {@link ViewerPlugin} to EMI: a mod's EMI plugin subclasses this once ({@code @EmiEntrypoint} on Forge-likes,
 * the {@code emi} entrypoint on Fabric). A native plugin also makes EMI's JEI layer skip the mod's own JEI categories,
 * so nothing is listed twice. A recipe with no {@link ViewerCategory#recipeId} gets a synthetic id ({@code ns:/...}, EMI's
 * convention). EMI's API has no click areas, so {@link ViewerPlugin#clickAreas} is not read.
 ^/
public abstract class EmiViewerPlugin implements EmiPlugin {

    private static final List<EmiViewerPlugin> PLUGINS = new CopyOnWriteArrayList<>();
    private static boolean harnessQuery;

    private final ViewerPlugin plugin;
    private final Subtypes.Declarations subtypes;
    private final Map<ViewerType<?>, ViewerCategory<?>> categories = new LinkedHashMap<>();
    private final Map<ViewerType<?>, EmiRecipeCategory> emiCategories = new LinkedHashMap<>();
    private final Map<ViewerType<?>, Integer> synthetic = new LinkedHashMap<>();
    private final Set<ResourceLocation> usedIds = new HashSet<>();

    protected EmiViewerPlugin(ViewerPlugin plugin, Subtypes.Declarations subtypes) {
        this.plugin = plugin;
        this.subtypes = subtypes;
        registerHarnessQuery();
    }

    @Override
    public void register(EmiRegistry registry) {
        // EMI builds new instances on Forge-likes; the one that registered is the one its manager knows
        PLUGINS.removeIf(other -> other.getClass() == getClass());
        PLUGINS.add(this);
        categories.clear();
        emiCategories.clear();
        synthetic.clear();
        usedIds.clear();
        registerSubtypes(registry);
        registry.addIngredientSerializer(IconFluidStack.class, new IconFluidStack.Serializer());
        plugin.categories(list -> {
            for (ViewerCategory<?> category : list) {
                EmiRecipeCategory emi = wrap(category);
                categories.put(category.type(), category);
                emiCategories.put(category.type(), emi);
                registry.addCategory(emi);
            }
        });
        plugin.stations(new ViewerPlugin.Stations() {
            @Override
            public void add(ViewerType<?> type, ItemStack... stations) {
                for (ItemStack station : stations) registry.addWorkstation(emiCategories.get(type), EmiStack.of(station));
            }

            @Override
            public void addSmelting(ItemStack... stations) {
                for (ItemStack station : stations) registry.addWorkstation(VanillaEmiRecipeCategories.SMELTING, EmiStack.of(station));
            }
        });
        plugin.recipes(new ViewerPlugin.Recipes() {
            @Override
            public <T> void add(ViewerType<T> type, List<T> recipes) {
                addRecipes(registry, type, recipes);
            }
        });
        plugin.runtime(new ViewerPlugin.Runtime() {
            @Override
            public String viewer() {
                return "EMI";
            }

            @Override
            public List<Map.Entry<ItemStack, ItemStack>> smeltingRecipes() {
                return Recipes.smeltingPairs(Minecraft.getInstance().level);
            }

            @Override
            public <T> void addRecipes(ViewerType<T> type, List<T> recipes) {
                EmiViewerPlugin.this.addRecipes(registry, type, recipes);
            }
        });
        plugin.ingredients(stacks -> {
            Set<Object> iconFluids = new HashSet<>();
            for (ItemStack stack : stacks) {
                IconFluidStack fluid = IconFluidStack.fromIcon(stack);
                if (fluid == null) {
                    registry.addEmiStack(EmiStack.of(stack));
                } else if (fluid.getId().getNamespace().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())
                        && iconFluids.add(fluid.getKey())) {
                    registry.addEmiStack(fluid.setAmount(0));
                }
            }
            // EMI lists every fluid: the icon replaces the mod's own, another mod's (water) stays
            registry.removeEmiStacks(stack -> stack.getClass() == FluidEmiStack.class && iconFluids.contains(stack.getKey()));
        });
        plugin.hidden(stacks -> {
            Set<EmiStack> hidden = new HashSet<>();
            for (ItemStack stack : stacks) hidden.add(EmiStack.of(stack));
            registry.removeEmiStacks(hidden::contains);
        });
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
                registry.addRecipeHandler(menuType, new SlotHandler<C, R>(type, recipeSlots, inventorySlotStart,
                        inventorySlotCount, canHandle));
            }

            @Override
            public <C extends AbstractContainerMenu, R> void addAction(Class<? extends C> menuClass,
                    MenuType<C> menuType, ViewerType<R> type, TransferAction<C, R> action) {
                registry.addRecipeHandler(menuType, new ActionHandler<>(type, action));
            }
        });
    }

    private static EmiRecipeCategory wrap(ViewerCategory<?> category) {
        ViewerType<?> type = category.type();
        return new EmiRecipeCategory(Ids.of(type.namespace(), type.path()), EmiStack.of(category.icon())) {
            @Override
            public Component getName() {
                return category.title();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> void addRecipes(EmiRegistry registry, ViewerType<T> type, List<T> recipes) {
        ViewerCategory<T> category = (ViewerCategory<T>) categories.get(type);
        if (category == null) return;
        EmiRecipeCategory emi = emiCategories.get(type);
        for (T recipe : recipes) registry.addRecipe(new ViewerRecipe<>(category, emi, recipe, idFor(category, recipe)));
    }

    /^* The recipe's own id the first time it is seen; a table row can show in two categories, and EMI wants ids unique. ^/
    private <T> ResourceLocation idFor(ViewerCategory<T> category, T recipe) {
        String named = category.recipeId(recipe);
        if (named != null) {
            ResourceLocation id = Ids.parse(named);
            if (usedIds.add(id)) return id;
        }
        ViewerType<T> type = category.type();
        int index = synthetic.merge(type, 1, Integer::sum);
        return Ids.of(type.namespace(), "/" + type.path() + "/" + index);
    }

    private void registerSubtypes(EmiRegistry registry) {
        subtypes.register(new Subtypes() {
            @Override
            public void components(Item item, GolemComponent<?>... components) {
                if (components.length > 0) compareBy(item, stack -> key(stack, components));
            }

            @Override
            public void listed(Item item, GolemComponent<?>... components) {
                components(item, components);
            }

            @Override
            public void interpreter(Item item, Function<ItemStack, String> interpreter) {
                compareBy(item, interpreter);
            }

            private void compareBy(Item item, Function<ItemStack, String> key) {
                registry.setDefaultComparison(item, Comparison.compareData(stack -> key.apply(stack.getItemStack())));
            }
        });
    }

    private static String key(ItemStack stack, GolemComponent<?>... components) {
        StringBuilder key = new StringBuilder();
        for (GolemComponent<?> component : components) {
            Object value = component.get(stack).orElse(null);
            key.append('|').append(value == null ? "" : value);
        }
        return key.toString();
    }

    private static final class SlotHandler<C extends AbstractContainerMenu, R> implements StandardRecipeHandler<C> {
        private final ViewerType<R> type;
        private final int[] recipeSlots;
        private final int inventoryStart;
        private final int inventoryCount;
        private final Predicate<R> canHandle;

        SlotHandler(ViewerType<R> type, int[] recipeSlots, int inventoryStart, int inventoryCount, Predicate<R> canHandle) {
            this.type = type;
            this.recipeSlots = recipeSlots;
            this.inventoryStart = inventoryStart;
            this.inventoryCount = inventoryCount;
            this.canHandle = canHandle;
        }

        @Override
        public List<Slot> getInputSources(C menu) {
            List<Slot> slots = new ArrayList<>(inventoryCount + recipeSlots.length);
            for (int i = inventoryStart; i < inventoryStart + inventoryCount; i++) slots.add(menu.getSlot(i));
            slots.addAll(getCraftingSlots(menu));
            return slots;
        }

        @Override
        public List<Slot> getCraftingSlots(C menu) {
            List<Slot> slots = new ArrayList<>(recipeSlots.length);
            for (int index : recipeSlots) slots.add(menu.getSlot(index));
            return slots;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean supportsRecipe(EmiRecipe recipe) {
            return recipe instanceof ViewerRecipe<?> viewer && viewer.category().type() == type && canHandle.test((R) viewer.recipe());
        }

        @Override
        public boolean alwaysDisplaySupport(EmiRecipe recipe) {
            return false;
        }

        @Override
        public boolean canCraft(EmiRecipe recipe, EmiCraftContext<C> context) {
            return StandardRecipeHandler.super.canCraft(items(recipe), context);
        }

        @Override
        public boolean craft(EmiRecipe recipe, EmiCraftContext<C> context) {
            return StandardRecipeHandler.super.craft(items(recipe), context);
        }

        private static EmiRecipe items(EmiRecipe recipe) {
            return recipe instanceof ViewerRecipe<?> viewer ? viewer.items() : recipe;
        }
    }

    private static final class ActionHandler<C extends AbstractContainerMenu, R> implements EmiRecipeHandler<C> {
        private final ViewerType<R> type;
        private final TransferAction<C, R> action;

        ActionHandler(ViewerType<R> type, TransferAction<C, R> action) {
            this.type = type;
            this.action = action;
        }

        @Override
        public EmiPlayerInventory getInventory(AbstractContainerScreen<C> screen) {
            return new EmiPlayerInventory(List.of());
        }

        @Override
        public boolean supportsRecipe(EmiRecipe recipe) {
            return recipe instanceof ViewerRecipe<?> viewer && viewer.category().type() == type;
        }

        @Override
        public boolean alwaysDisplaySupport(EmiRecipe recipe) {
            return false;
        }

        @SuppressWarnings("unchecked")
        private R recipe(EmiRecipe recipe) {
            return (R) ((ViewerRecipe<?>) recipe).recipe();
        }

        @Override
        public boolean canCraft(EmiRecipe recipe, EmiCraftContext<C> context) {
            return action.unavailable(context.getScreenHandler(), recipe(recipe)) == null;
        }

        @Override
        public boolean craft(EmiRecipe recipe, EmiCraftContext<C> context) {
            TransferAction.runWhenBack(context.getScreenHandler(), recipe(recipe), action);
            return true;
        }

        @Override
        public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<C> context) {
            Component why = action.unavailable(context.getScreenHandler(), recipe(recipe));
            return why == null ? List.of() : List.of(ClientTooltipComponent.create(why.getVisualOrderText()));
        }
    }

    /^*
     * The harness query {@code emi} (dev clients only): {@code op=status} counts each plugin's categories and recipes as
     * EMI holds them; {@code op=open category} opens one tab; {@code op=fill category item} fills the open menu from the
     * first recipe taking the item; {@code op=recipe id} reads a recipe's output slot context and sets it as the tree goal;
     * {@code op=lookup fluid|item [show]} counts (and opens) its recipes and uses; {@code op=index namespace} counts its
     * fluids' list entries; {@code op=search text} sets the list's search.
     ^/
    private static synchronized void registerHarnessQuery() {
        if (harnessQuery) return;
        harnessQuery = true;
        HarnessQueryRegistry.register("emi", (mc, params) -> {
            JsonObject out = new JsonObject();
            var manager = EmiApi.getRecipeManager();
            String op = params.has("op") ? params.get("op").getAsString() : "status";
            switch (op) {
                case "open" -> {
                    ResourceLocation id = Ids.parse(params.get("category").getAsString());
                    for (EmiRecipeCategory category : manager.getCategories()) {
                        if (category.getId().equals(id)) {
                            EmiApi.displayRecipeCategory(category);
                            out.addProperty("opened", true);
                        }
                    }
                }
                case "fill" -> {
                    ResourceLocation id = Ids.parse(params.get("category").getAsString());
                    EmiStack wanted = EmiStack.of(BuiltInRegistries.ITEM.getValue(Ids.parse(params.get("item").getAsString())));
                    if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
                        out.addProperty("error", "no container screen: " + mc.screen);
                        return out;
                    }
                    for (EmiRecipe recipe : manager.getRecipesByInput(wanted)) {
                        if (!recipe.getCategory().getId().equals(id)) continue;
                        out.addProperty("recipe", String.valueOf(recipe.getId()));
                        out.addProperty("filled", EmiRecipeFiller.performFill(recipe, screen,
                                EmiCraftContext.Type.FILL_BUTTON, EmiCraftContext.Destination.NONE, 1));
                        return out;
                    }
                    out.addProperty("error", "no recipe");
                }
                case "lookup" -> {
                    // recipes (by output) and uses (by input) of a fluid or item, per category, as EMI's R and U find them
                    EmiStack wanted = params.has("fluid")
                            ? EmiStack.of(BuiltInRegistries.FLUID.getOptional(Ids.parse(params.get("fluid").getAsString())).orElseThrow())
                            : EmiStack.of(BuiltInRegistries.ITEM.getOptional(Ids.parse(params.get("item").getAsString())).orElseThrow());
                    out.add("recipes", perCategory(manager.getRecipesByOutput(wanted)));
                    out.add("uses", perCategory(manager.getRecipesByInput(wanted)));
                    if (params.has("show")) {
                        if (params.get("show").getAsString().equals("uses")) EmiApi.displayUses(wanted);
                        else EmiApi.displayRecipes(wanted);
                    }
                }
                case "search" -> EmiApi.setSearchText(params.get("text").getAsString());
                case "index" -> {
                    // the list entries of one namespace's fluids, by stack class, and the entries of one item
                    String namespace = params.get("namespace").getAsString();
                    String item = params.has("item") ? params.get("item").getAsString() : "";
                    JsonObject classes = new JsonObject();
                    JsonArray plain = new JsonArray();
                    int items = 0;
                    for (EmiStack stack : EmiApi.getIndexStacks()) {
                        if (stack.getKey() instanceof Fluid && stack.getId().getNamespace().equals(namespace)) {
                            String name = stack.getClass().getSimpleName();
                            classes.addProperty(name, classes.has(name) ? classes.get(name).getAsInt() + 1 : 1);
                            if (!(stack instanceof IconFluidStack)) plain.add(stack.getId().toString());
                            else if (!out.has("saved")) {
                                // what a favourite of it reads back as
                                EmiIngredient saved = EmiIngredientSerializer.getDeserialized(EmiIngredientSerializer.getSerialized(stack));
                                out.addProperty("saved", saved.getClass().getSimpleName() + " " + saved.equals(stack));
                            }
                        }
                        if (!stack.getItemStack().isEmpty() && stack.getId().toString().equals(item)) items++;
                    }
                    out.add("fluids", classes);
                    out.add("plain", plain);
                    out.addProperty("items", items);
                }
                case "recipe" -> {
                    EmiRecipe recipe = manager.getRecipe(Ids.parse(params.get("id").getAsString()));
                    if (recipe == null) {
                        out.addProperty("error", "no recipe");
                        return out;
                    }
                    out.addProperty("output_has_context", outputHasContext(recipe));
                    out.addProperty("supports_tree", recipe.supportsRecipeTree());
                    BoM.setGoal(recipe);
                    out.addProperty("tree_goal", BoM.tree != null ? String.valueOf(BoM.tree.goal.recipe.getId()) : "");
                }
                default -> {
                    int categories = 0, recipes = 0;
                    JsonArray ids = new JsonArray();
                    JsonArray sample = new JsonArray();
                    for (EmiViewerPlugin plugin : PLUGINS) {
                        for (EmiRecipeCategory category : plugin.emiCategories.values()) {
                            List<EmiRecipe> list = manager.getRecipes(category);
                            categories++;
                            recipes += list.size();
                            ids.add(category.getId() + "=" + list.size());
                            if (!list.isEmpty() && sample.size() < 80) sample.add(String.valueOf(list.get(0).getId()));
                        }
                    }
                    out.addProperty("categories", categories);
                    out.addProperty("recipes", recipes);
                    out.add("ids", ids);
                    out.add("sample", sample);
                }
            }
            return out;
        });
    }

    private static JsonObject perCategory(List<EmiRecipe> recipes) {
        JsonObject counts = new JsonObject();
        for (EmiRecipe recipe : recipes) {
            String id = recipe.getCategory().getId().toString();
            counts.addProperty(id, counts.has(id) ? counts.get(id).getAsInt() + 1 : 1);
        }
        return counts;
    }

    private static boolean outputHasContext(EmiRecipe recipe) {
        List<Widget> widgets = new ArrayList<>();
        recipe.addWidgets(new WidgetHolder() {
            @Override public int getWidth() { return recipe.getDisplayWidth(); }
            @Override public int getHeight() { return recipe.getDisplayHeight(); }

            @Override
            public <W extends Widget> W add(W widget) {
                widgets.add(widget);
                return widget;
            }
        });
        return widgets.stream().anyMatch(widget -> widget instanceof SlotWidget slot && slot.getRecipe() == recipe);
    }
}
*///?}
