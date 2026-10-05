package dev.golem.core.jei;

//? if jei {

import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
// No DataComponentType import: the type is only ever inferred here (through GolemComponent.type()),
// and naming it would break 1.20.1, where net.minecraft.core.component does not exist at all.
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The parts of JEI's registration API that differ between its 15.x, 19.x, and 29.x lines, in one place.
 *
 * <p>Everything here exists so a mod's plugin reads the same on every target. Each method is the 29.x
 * spelling — the line this port is written against — implemented on the 15.x and 19.x lines in terms of
 * whatever those lines call the same thing. Compiled out entirely on a node that declares no JEI coordinates.
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

    /** Registers a machine's clickable recipe area on every supported JEI API line. */
    public static void addRecipeClickArea(IGuiHandlerRegistration registration,
                                          Class<? extends AbstractContainerScreen<?>> screenClass,
                                          int x, int y, int width, int height, JeiRecipeType<?> type) {
        registration.addRecipeClickArea(screenClass, x, y, width, height, type.unwrap());
    }

    /** Registers JEI's slot-range transfer handler without exposing its version-specific recipe type. */
    public static <C extends AbstractContainerMenu, R> void addRecipeTransferHandler(
            IRecipeTransferRegistration registration, Class<? extends C> containerClass, MenuType<C> menuType,
            JeiRecipeType<R> type, int recipeSlotStart, int recipeSlotCount,
            int inventorySlotStart, int inventorySlotCount) {
        registration.addRecipeTransferHandler(containerClass, menuType, type.unwrap(), recipeSlotStart,
                recipeSlotCount, inventorySlotStart, inventorySlotCount);
    }

    /** Registers a transfer handler for non-contiguous machine slots, optionally excluding recipes that
     * contain display-only ingredients such as HBM fluid droplets. */
    public static <C extends AbstractContainerMenu, R> void addSelectiveRecipeTransferHandler(
            IRecipeTransferRegistration registration, Class<? extends C> containerClass, MenuType<C> menuType,
            JeiRecipeType<R> type, int[] recipeSlotIndices, int inventorySlotStart, int inventorySlotCount,
            Predicate<R> canHandle) {
        //? if >=26.1 {
        registration.addRecipeTransferHandler(new mezz.jei.api.recipe.transfer.IRecipeTransferInfo<C, R>() {
            @Override public Class<? extends C> getContainerClass() { return containerClass; }
            @Override public Optional<MenuType<C>> getMenuType() { return Optional.of(menuType); }
            @Override public mezz.jei.api.recipe.types.IRecipeType<R> getRecipeType() { return type.unwrap(); }
            @Override public boolean canHandle(C menu, R recipe) { return canHandle.test(recipe); }
            @Override public List<net.minecraft.world.inventory.Slot> getRecipeSlots(C menu, R recipe) {
                List<net.minecraft.world.inventory.Slot> slots = new ArrayList<>(recipeSlotIndices.length);
                for (int index : recipeSlotIndices) slots.add(menu.getSlot(index));
                return slots;
            }
            @Override public List<net.minecraft.world.inventory.Slot> getInventorySlots(C menu, R recipe) {
                return new ArrayList<>(menu.slots.subList(inventorySlotStart,
                        inventorySlotStart + inventorySlotCount));
            }
        });
        //?} else {
        /*registration.addRecipeTransferHandler(new mezz.jei.api.recipe.transfer.IRecipeTransferInfo<C, R>() {
            @Override public Class<? extends C> getContainerClass() { return containerClass; }
            @Override public Optional<MenuType<C>> getMenuType() { return Optional.of(menuType); }
            @Override public mezz.jei.api.recipe.RecipeType<R> getRecipeType() { return type.unwrap(); }
            @Override public boolean canHandle(C menu, R recipe) { return canHandle.test(recipe); }
            @Override public List<net.minecraft.world.inventory.Slot> getRecipeSlots(C menu, R recipe) {
                List<net.minecraft.world.inventory.Slot> slots = new ArrayList<>(recipeSlotIndices.length);
                for (int index : recipeSlotIndices) slots.add(menu.getSlot(index));
                return slots;
            }
            @Override public List<net.minecraft.world.inventory.Slot> getInventorySlots(C menu, R recipe) {
                return new ArrayList<>(menu.slots.subList(inventorySlotStart,
                        inventorySlotStart + inventorySlotCount));
            }
        });
        *///?}
    }

    /**
     * Makes an item's JEI identity depend on data components, so stacks differing only in those
     * components (a fluid icon's fluid, amount, or pressure, say) are treated as distinct ingredients.
     *
     * <p>29.x offers {@code registerFromDataComponentTypes} directly. 19.x predates it and takes a
     * subtype interpreter, so this supplies one that reads the same component — the component API
     * itself is unchanged between the two, which is what makes the reimplementation exact.
     */
    public static void registerFromDataComponentTypes(ISubtypeRegistration registration, Item item,
                                                      dev.golem.core.data.GolemComponent<?>... components) {
        if (components.length == 0) return;
        //? if >=26.1 {
        registration.registerFromDataComponentTypes(item,
                java.util.Arrays.stream(components)
                        .map(dev.golem.core.data.GolemComponent::type)
                        .toArray(net.minecraft.core.component.DataComponentType[]::new));
        //?} else {
        /*registration.registerSubtypeInterpreter(item, (stack, context) -> {
            StringBuilder subtype = new StringBuilder();
            for (dev.golem.core.data.GolemComponent<?> component : components) {
                Object value = component.get(stack).orElse(null);
                subtype.append('|').append(value == null ? "" : value);
            }
            return subtype.toString();
        });
        *///?}
    }

    /** Registers an item whose identity includes state that is not a data component. */
    public static void registerSubtypeInterpreter(ISubtypeRegistration registration, Item item,
                                                  java.util.function.Function<ItemStack, String> interpreter) {
        registration.registerSubtypeInterpreter(item, (stack, context) -> interpreter.apply(stack));
    }

    // ------------------------------------------------------------------------------------------- runtime

    /** Adds recipes of one type once JEI's runtime exists ({@code IModPlugin.onRuntimeAvailable}). */
    public static <T> void addRecipes(mezz.jei.api.runtime.IJeiRuntime runtime, JeiRecipeType<T> type, List<T> recipes) {
        if (!recipes.isEmpty()) runtime.getRecipeManager().addRecipes(type.unwrap(), recipes);
    }

    /**
     * Every vanilla furnace (smelting) recipe JEI knows, as one (input, result) pair per input item. JEI's own lookup is
     * the only place a 26.1 client can read them (the client no longer receives the recipe manager); the older lines
     * read the same recipes through the same lookup, so all three answer alike.
     */
    public static List<java.util.Map.Entry<ItemStack, ItemStack>> smeltingRecipes(mezz.jei.api.runtime.IJeiRuntime runtime) {
        List<java.util.Map.Entry<ItemStack, ItemStack>> out = new ArrayList<>();
        //? if >=26.1 {
        runtime.getRecipeManager().createRecipeLookup(mezz.jei.api.constants.RecipeTypes.SMELTING).get().forEach(holder -> {
            var recipe = holder.value();
            recipe.input().items().forEach(item -> {
                ItemStack in = new ItemStack(item);
                ItemStack result = recipe.assemble(new net.minecraft.world.item.crafting.SingleRecipeInput(in));
                if (!result.isEmpty()) out.add(java.util.Map.entry(in, result));
            });
        });
        //?} elif >=1.21.1 {
        /*var registries = net.minecraft.client.Minecraft.getInstance().level.registryAccess();
        runtime.getRecipeManager().createRecipeLookup(mezz.jei.api.constants.RecipeTypes.SMELTING).get().forEach(holder -> {
            var recipe = holder.value();
            if (recipe.getIngredients().isEmpty()) return;
            ItemStack result = recipe.getResultItem(registries);
            if (result.isEmpty()) return;
            for (ItemStack in : recipe.getIngredients().get(0).getItems()) out.add(java.util.Map.entry(in.copyWithCount(1), result.copy()));
        });
        *///?} else {
        /*var registries = net.minecraft.client.Minecraft.getInstance().level.registryAccess();
        runtime.getRecipeManager().createRecipeLookup(mezz.jei.api.constants.RecipeTypes.SMELTING).get().forEach(recipe -> {
            if (recipe.getIngredients().isEmpty()) return;
            ItemStack result = recipe.getResultItem(registries);
            if (result.isEmpty()) return;
            for (ItemStack in : recipe.getIngredients().get(0).getItems()) out.add(java.util.Map.entry(in.copyWithCount(1), result.copy()));
        });
        *///?}
        return out;
    }
}
//?}
