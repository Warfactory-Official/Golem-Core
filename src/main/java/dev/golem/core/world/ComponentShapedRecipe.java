package dev.golem.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * A shaped recipe whose ingredient predicates include exact item component state.
 *
 * <p>With a {@code copy_key} symbol, the result takes every component of the stack matched by that
 * symbol - 1.7's {@code ContainerUpgradeCraftingHandler}, which set the crafted crate's NBT to the
 * ingredient crate's so the contents survive the upgrade.
 */
public class ComponentShapedRecipe extends CompatCustomRecipe {

    public static final RecipeSerializer<ComponentShapedRecipe> SERIALIZER =
            CustomRecipes.componentShaped(ComponentShapedRecipe::new);

    private final List<String> pattern;
    private final Map<String, ComponentIngredient> key;
    private final List<ComponentIngredient> ingredients;
    private final int width;
    private final int height;
    private final ItemStackSpec result;
    private final String copyFrom;

    protected ComponentShapedRecipe(String namespace, String path, List<String> pattern,
                                    Map<String, ComponentIngredient> key, ItemStackSpec result) {
        this(namespace, path, pattern, key, result, "");
    }

    protected ComponentShapedRecipe(String namespace, String path, List<String> pattern,
                                    Map<String, ComponentIngredient> key, ItemStackSpec result, String copyFrom) {
        super(namespace, path);
        this.copyFrom = copyFrom == null ? "" : copyFrom;
        if (!this.copyFrom.isEmpty() && !key.containsKey(this.copyFrom)) {
            throw new IllegalArgumentException("copy_key names no key symbol: " + this.copyFrom);
        }
        if (pattern.isEmpty()) throw new IllegalArgumentException("empty shaped recipe pattern");
        this.pattern = List.copyOf(pattern);
        this.key = Map.copyOf(key);
        this.width = this.pattern.stream().mapToInt(String::length).max().orElse(0);
        this.height = this.pattern.size();
        if (width == 0 || width > 3 || height > 3) {
            throw new IllegalArgumentException("shaped recipe exceeds the crafting grid");
        }

        List<ComponentIngredient> flattened = new ArrayList<>(width * height);
        for (String row : this.pattern) {
            if (row.length() > width) throw new IllegalArgumentException("inconsistent shaped row width");
            for (int x = 0; x < width; x++) {
                String symbol = x < row.length() ? String.valueOf(row.charAt(x)) : " ";
                if (symbol.equals(" ")) {
                    flattened.add(ComponentIngredient.EMPTY);
                } else {
                    ComponentIngredient ingredient = this.key.get(symbol);
                    if (ingredient == null) throw new IllegalArgumentException("missing shaped key: " + symbol);
                    flattened.add(ingredient);
                }
            }
        }
        this.ingredients = List.copyOf(flattened);
        this.result = result;
    }

    public final List<String> pattern() {
        return pattern;
    }

    public final Map<String, ComponentIngredient> key() {
        return key;
    }

    public final ItemStack result() {
        return result.create();
    }

    /** The key symbol whose stack the result copies its components from, or {@code ""}. */
    public final String copyFrom() {
        return copyFrom;
    }

    //? if >=26.1 {
    public final net.minecraft.world.item.ItemStackTemplate resultTemplate() {
        return result.template();
    }
    //?}

    @Override
    public boolean isSpecial() {
        return false;
    }

    @Override
    protected boolean matchesItems(List<ItemStack> input, Level level) {
        return matchesGrid(width, height, input, level);
    }

    @Override
    protected boolean matchesGrid(int gridWidth, int gridHeight, List<ItemStack> input, Level level) {
        if (gridWidth <= 0 || gridHeight <= 0 || input.size() != gridWidth * gridHeight
                || width > gridWidth || height > gridHeight) return false;
        for (boolean mirrored : new boolean[] {false, true}) {
            for (int offsetY = 0; offsetY <= gridHeight - height; offsetY++) {
                for (int offsetX = 0; offsetX <= gridWidth - width; offsetX++) {
                    if (matchesAt(gridWidth, gridHeight, input, mirrored, offsetX, offsetY)) return true;
                }
            }
        }
        return false;
    }

    private boolean matchesAt(int gridWidth, int gridHeight, List<ItemStack> input, boolean mirrored,
                              int offsetX, int offsetY) {
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                int recipeX = x - offsetX;
                int recipeY = y - offsetY;
                ComponentIngredient expected = ComponentIngredient.EMPTY;
                if (recipeX >= 0 && recipeX < width && recipeY >= 0 && recipeY < height) {
                    int sourceX = mirrored ? width - recipeX - 1 : recipeX;
                    expected = ingredients.get(recipeY * width + sourceX);
                }
                ItemStack actual = input.get(y * gridWidth + x);
                if (expected.isEmpty() ? !actual.isEmpty() : !expected.test(actual)) return false;
            }
        }
        return true;
    }

    @Override
    protected ItemStack assembleItems(List<ItemStack> input) {
        return assembleGrid(width, height, input);
    }

    @Override
    protected ItemStack assembleGrid(int gridWidth, int gridHeight, List<ItemStack> input) {
        if (!matchesGrid(gridWidth, gridHeight, input, null)) return ItemStack.EMPTY;
        ItemStack assembled = result.create();
        if (!copyFrom.isEmpty()) {
            ComponentIngredient source = key.get(copyFrom);
            for (int slot = 0; slot < input.size(); slot++) {
                // the first stack in grid order, as ContainerUpgradeCraftingHandler.getFirstContainer
                if (expectedAt(gridWidth, gridHeight, input, slot) == source) {
                    ItemStack copied = assembled.copy();
                    ItemStacks.copyCustomState(input.get(slot), copied);
                    return copied;
                }
            }
        }
        return assembled;
    }

    private ComponentIngredient expectedAt(int gridWidth, int gridHeight, List<ItemStack> input,
                                           int slot) {
        for (boolean mirrored : new boolean[] {false, true}) {
            for (int offsetY = 0; offsetY <= gridHeight - height; offsetY++) {
                for (int offsetX = 0; offsetX <= gridWidth - width; offsetX++) {
                    if (!matchesAt(gridWidth, gridHeight, input, mirrored, offsetX, offsetY)) continue;
                    int x = slot % gridWidth - offsetX;
                    int y = slot / gridWidth - offsetY;
                    if (x < 0 || x >= width || y < 0 || y >= height) return ComponentIngredient.EMPTY;
                    int sourceX = mirrored ? width - x - 1 : x;
                    return ingredients.get(y * width + sourceX);
                }
            }
        }
        return ComponentIngredient.EMPTY;
    }

    private NonNullList<ItemStack> remaining(int gridWidth, int gridHeight, List<ItemStack> input) {
        NonNullList<ItemStack> result = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.get(slot);
            if (stack.isEmpty()) continue;
            ItemStack remainder = expectedAt(gridWidth, gridHeight, input, slot).remainderFor(stack);
            if (!remainder.isEmpty()) result.set(slot, remainder);
        }
        return result;
    }

    //? if >=1.21.1 {
    @Override
    public NonNullList<ItemStack> getRemainingItems(net.minecraft.world.item.crafting.CraftingInput input) {
        return remaining(input.width(), input.height(), items(input));
    }
    //?} else {
    /*@Override
    public NonNullList<ItemStack> getRemainingItems(net.minecraft.world.inventory.CraftingContainer input) {
        List<ItemStack> stacks = new ArrayList<>(input.getContainerSize());
        for (int slot = 0; slot < input.getContainerSize(); slot++) stacks.add(input.getItem(slot));
        return remaining(input.getWidth(), input.getHeight(), stacks);
    }
    *///?}

    //? if >=26.1 {
    @Override
    public net.minecraft.world.item.crafting.PlacementInfo placementInfo() {
        return net.minecraft.world.item.crafting.PlacementInfo.createFromOptionals(ingredients.stream()
                .map(ingredient -> ingredient.isEmpty()
                        ? java.util.Optional.<Ingredient>empty()
                        : java.util.Optional.of(ingredient.displayIngredient()))
                .toList());
    }

    @Override
    public List<net.minecraft.world.item.crafting.display.RecipeDisplay> display() {
        return List.of(new net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay(
                width,
                height,
                ingredients.stream().map(ingredient -> ingredient.isEmpty()
                        ? net.minecraft.world.item.crafting.display.SlotDisplay.Empty.INSTANCE
                        : ingredient.displayIngredient().display()).toList(),
                new net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay(resultTemplate()),
                new net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }
    //?}

    //? if <26.1 {
    /*@Override
    public boolean canCraftInDimensions(int gridWidth, int gridHeight) {
        return width <= gridWidth && height <= gridHeight;
    }

    @Override
    public net.minecraft.core.NonNullList<Ingredient> getIngredients() {
        net.minecraft.core.NonNullList<Ingredient> copy = net.minecraft.core.NonNullList.create();
        for (ComponentIngredient ingredient : ingredients) copy.add(ingredient.displayIngredient());
        return copy;
    }
    *///?}

    //? if >=1.21.1 && <26.1 {
    /*@Override
    public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider registries) {
        return result.create();
    }
    *///?}

    //? if <1.21.1 {
    /*@Override
    public ItemStack getResultItem(net.minecraft.core.RegistryAccess registries) {
        return result.create();
    }
    *///?}

    @Override
    protected RecipeSerializer<?> serializer() {
        return SERIALIZER;
    }
}
