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
 * component read and {@code DyeItem} no longer answers it at all - a port that keeps the old call
 * does not misbehave, it fails to compile, which is the good case.
 *
 * <p>Colours come from {@link DyeColor}. {@code getFireworkColor()} is the table legacy
 * {@code ItemDye.field_150922_c} was; note that {@code DyeColor}'s ids run opposite to 1.7's damage
 * values, so a port that reads the old table by index gets the complementary colour.
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
