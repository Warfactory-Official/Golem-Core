package dev.golem.core.world;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * "Which dye is this stack, if any?" across the move to a data component.
 *
 * <p>Through 1.21.1 a dye is a {@code DyeItem} and its colour is a field on the item, so the answer
 * is an {@code instanceof} plus {@code getDyeColor()}. On 26.1 the sixteen dyes share one item class
 * and the colour is carried by {@code DataComponents.DYE} on the stack, so the same question is a
 * component read and {@code DyeItem} does not answer it at all; the old call fails to compile.
 *
 * <p>Colours come from {@link DyeColor}. {@code getFireworkColor()} is legacy
 * {@code ItemDye.field_150922_c}; {@code DyeColor}'s ids run opposite to 1.7's damage values, so reading
 * the old table by index gives the complementary colour.
 */
public final class Dyes {

    private Dyes() {}

    /** The dye colour this stack carries, or null when it is not a dye. */
    public static @Nullable DyeColor colorOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        //? if >=26.1 {
        return stack.get(net.minecraft.core.component.DataComponents.DYE);
        //?} else {
        /*return stack.getItem() instanceof net.minecraft.world.item.DyeItem dye ? dye.getDyeColor() : null;
        *///?}
    }
}
