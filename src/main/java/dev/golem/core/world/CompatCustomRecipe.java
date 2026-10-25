package dev.golem.core.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

//? if >=1.21.1 {
import net.minecraft.world.item.crafting.CraftingInput;
//?} else {
/*import net.minecraft.world.inventory.CraftingContainer;
*///?}

/**
 * A custom crafting recipe with version-neutral item input and result hooks.
 *
 * <p>The recipe interfaces changed twice between the supported targets; recipes only implement the
 * two hooks below while this class owns the vanilla bridge methods. It is not a vanilla {@code CustomRecipe}:
 * EMI lists no crafting recipe of that class, so a non-special subclass would be invisible there.
 */
public abstract class CompatCustomRecipe implements CraftingRecipe {

    //? if <1.21.1
    /*private final net.minecraft.resources.ResourceLocation id;*/

    protected CompatCustomRecipe(String namespace, String path) {
        //? if <1.21.1
        /*this.id = dev.golem.core.Ids.of(namespace, path);*/
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    /** Hidden from recipe books and viewers unless a subclass describes its grid. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    //? if >=26.1 {
    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public net.minecraft.world.item.crafting.PlacementInfo placementInfo() {
        return net.minecraft.world.item.crafting.PlacementInfo.NOT_PLACEABLE;
    }
    //?} else if >=1.21.1 {
    /*@Override
    public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }
    *///?} else {
    /*@Override
    public net.minecraft.resources.ResourceLocation getId() {
        return id;
    }

    @Override
    public ItemStack getResultItem(net.minecraft.core.RegistryAccess registries) {
        return ItemStack.EMPTY;
    }
    *///?}

    protected abstract boolean matchesItems(List<ItemStack> input, Level level);

    protected abstract ItemStack assembleItems(List<ItemStack> input);

    /** Grid-aware hook for recipes whose shaped position is part of their contract. */
    protected boolean matchesGrid(int width, int height, List<ItemStack> input, Level level) {
        return matchesItems(input, level);
    }

    /** Grid-aware assembly hook paired with {@link #matchesGrid}. */
    protected ItemStack assembleGrid(int width, int height, List<ItemStack> input) {
        return assembleItems(input);
    }

    /**
     * What the grid keeps in a slot once {@code consumed} is used up, slot by slot in grid order. The default is
     * the item's own crafting remainder (a bucket for a milk bucket); a recipe that keeps a tool in the grid
     * whole - 1.7's {@code getContainerItem} returning a copy - overrides this.
     */
    protected ItemStack remainder(List<ItemStack> input, int slot, ItemStack consumed) {
        return Compat.craftingRemainder(consumed);
    }

    private net.minecraft.core.NonNullList<ItemStack> remaining(List<ItemStack> input) {
        net.minecraft.core.NonNullList<ItemStack> left =
                net.minecraft.core.NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.get(slot);
            if (!stack.isEmpty()) left.set(slot, remainder(input, slot, stack));
        }
        return left;
    }

    /** Version-neutral entry points for machines and server-side validation. */
    public final boolean matchesItemStacks(List<ItemStack> input, Level level) {
        return matchesItems(input, level);
    }

    public final ItemStack assembleItemStacks(List<ItemStack> input) {
        return assembleItems(input);
    }

    protected abstract RecipeSerializer<?> serializer();

    //? if <26.1 {
    /*@Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }
    *///?}

    //? if >=1.21.1 {
    protected static List<ItemStack> items(CraftingInput input) {
        List<ItemStack> stacks = new ArrayList<>(input.size());
        for (int slot = 0; slot < input.size(); slot++) stacks.add(input.getItem(slot));
        return stacks;
    }

    @Override
    public final boolean matches(CraftingInput input, Level level) {
        return matchesGrid(input.width(), input.height(), items(input), level);
    }

    //? if >=26.1 {
    @Override
    public final ItemStack assemble(CraftingInput input) {
        return assembleGrid(input.width(), input.height(), items(input));
    }
    //?} else {
    /*@Override
    public final ItemStack assemble(CraftingInput input, net.minecraft.core.HolderLookup.Provider registries) {
        return assembleGrid(input.width(), input.height(), items(input));
    }
    *///?}
    //?} else {
    /*private static List<ItemStack> items(CraftingContainer input) {
        List<ItemStack> stacks = new ArrayList<>(input.getContainerSize());
        for (int slot = 0; slot < input.getContainerSize(); slot++) stacks.add(input.getItem(slot));
        return stacks;
    }

    @Override
    public final boolean matches(CraftingContainer input, Level level) {
        return matchesGrid(input.getWidth(), input.getHeight(), items(input), level);
    }

    @Override
    public final ItemStack assemble(CraftingContainer input, net.minecraft.core.RegistryAccess registries) {
        return assembleGrid(input.getWidth(), input.getHeight(), items(input));
    }
    *///?}

    //? if >=1.21.1 {
    @Override
    public net.minecraft.core.NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return remaining(items(input));
    }
    //?} else {
    /*@Override
    public net.minecraft.core.NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        return remaining(items(input));
    }
    *///?}

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public final RecipeSerializer getSerializer() {
        return serializer();
    }
}
