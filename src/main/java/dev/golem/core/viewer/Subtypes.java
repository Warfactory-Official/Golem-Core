package dev.golem.core.viewer;

import dev.golem.core.data.GolemComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

/**
 * Which stacks of one item a recipe viewer must keep apart: JEI's subtype registration, REI's item comparators.
 * Without it both viewers treat every stack of an item as one ingredient, so a fluid icon for oil and one for water
 * would collapse into a single list entry and look up each other's recipes.
 *
 * <p>Server-safe: REI registers comparators from its common plugin, which also runs on a dedicated server.
 */
public interface Subtypes {

    /** Stacks differ when any of these components differs. */
    void components(Item item, GolemComponent<?>... components);

    /** Stacks differ when the interpreter's strings differ. */
    void interpreter(Item item, Function<ItemStack, String> interpreter);

    /** A mod's subtype declarations. */
    @FunctionalInterface
    interface Declarations {
        void register(Subtypes subtypes);
    }
}
