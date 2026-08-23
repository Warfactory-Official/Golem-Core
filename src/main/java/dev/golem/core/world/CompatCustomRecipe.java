package dev.golem.core.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

//? if >=1.21.1 {
import net.minecraft.world.item.crafting.CraftingInput;
//?} else {
/*import net.minecraft.world.inventory.CraftingContainer;
*///?}
//? if <26.1
//import net.minecraft.world.item.crafting.CraftingBookCategory;

/**
 * A custom crafting recipe with version-neutral item input and result hooks.
 *
 * <p>The recipe interfaces changed twice between the supported targets; recipes only implement the
 * two hooks below while this class owns the vanilla bridge methods.
 */
public abstract class CompatCustomRecipe extends CustomRecipe {

    protected CompatCustomRecipe(String namespace, String path) {
        //? if >=26.1 {
        //?} else if >=1.21.1 {
        /*super(CraftingBookCategory.MISC);
        *///?} else {
        /*super(dev.golem.core.Ids.of(namespace, path), CraftingBookCategory.MISC);
        *///?}
    }

    protected abstract boolean matchesItems(List<ItemStack> input, Level level);

    protected abstract ItemStack assembleItems(List<ItemStack> input);

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
    public final boolean canCraftInDimensions(int width, int height) {
        return true;
    }
    *///?}

    //? if >=1.21.1 {
    private static List<ItemStack> items(CraftingInput input) {
        List<ItemStack> stacks = new ArrayList<>(input.size());
        for (int slot = 0; slot < input.size(); slot++) stacks.add(input.getItem(slot));
        return stacks;
    }

    @Override
    public final boolean matches(CraftingInput input, Level level) {
        return matchesItems(items(input), level);
    }

    //? if >=26.1 {
    @Override
    public final ItemStack assemble(CraftingInput input) {
        return assembleItems(items(input));
    }
    //?} else {
    /*@Override
    public final ItemStack assemble(CraftingInput input, net.minecraft.core.HolderLookup.Provider registries) {
        return assembleItems(items(input));
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
        return matchesItems(items(input), level);
    }

    @Override
    public final ItemStack assemble(CraftingContainer input, net.minecraft.core.RegistryAccess registries) {
        return assembleItems(items(input));
    }
    *///?}

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public final RecipeSerializer getSerializer() {
        return serializer();
    }
}
