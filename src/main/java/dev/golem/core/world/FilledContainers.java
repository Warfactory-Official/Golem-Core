package dev.golem.core.world;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/**
 * The mod's fluid-container dictionary, as a recipe ingredient sees it: which stacks hold a named fluid
 * in exactly a given amount, and what is left once that fluid is poured out.
 *
 * <p>This is 1.7's {@code container<amount><fluid>} ore-dictionary name ({@code FluidType.getDict}): a
 * recipe asked for "1,000 mB of lubricant in anything" and every registered full container answered,
 * leaving its empty container in the grid. The layer cannot know the mod's fluids or containers, so the
 * mod registers the one {@link Dictionary}; {@link ComponentIngredient} consults it for its
 * {@code fluid} constraint.
 */
public final class FilledContainers {

    /** The mod's container registry. */
    public interface Dictionary {
        /** Whether {@code stack} is a full container of exactly {@code amount} of {@code fluid}. */
        boolean holds(ItemStack stack, String fluid, int amount);

        /** What the crafting grid keeps once the container's fluid is consumed (empty for none). */
        ItemStack emptied(ItemStack stack);

        /** Stacks that satisfy {@link #holds}, for recipe displays. */
        List<ItemStack> examples(String fluid, int amount);
    }

    private static final Dictionary NONE = new Dictionary() {
        @Override public boolean holds(ItemStack stack, String fluid, int amount) { return false; }
        @Override public ItemStack emptied(ItemStack stack) { return ItemStack.EMPTY; }
        @Override public List<ItemStack> examples(String fluid, int amount) { return List.of(); }
    };

    private static volatile Dictionary dictionary = NONE;

    private FilledContainers() {}

    public static void register(Dictionary registry) {
        dictionary = Objects.requireNonNull(registry);
    }

    public static boolean holds(ItemStack stack, String fluid, int amount) {
        return stack != null && !stack.isEmpty() && dictionary.holds(stack, fluid, amount);
    }

    public static ItemStack emptied(ItemStack stack) {
        return dictionary.emptied(stack);
    }

    public static List<ItemStack> examples(String fluid, int amount) {
        return dictionary.examples(fluid, amount);
    }
}
