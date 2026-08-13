package dev.golem.core.data;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

//? if <26.1 {
/*import net.minecraft.world.item.component.DyedItemColor;
*///?}

/**
 * The {@code custom_model_data} component, which changed shape completely between versions.
 *
 * <p>Pre-26.1 it is a single {@code int} — a model index, nothing more. 26.1 turned it into four
 * parallel lists (floats, flags, strings, colours) so a model can be selected on several independent
 * axes at once. HBM uses two of those axes: a tint colour for fluid icons, and a boolean flag that
 * switches scrap items to their molten appearance.
 *
 * <p>Below 26.1 the single int is spent on the flag (a resource pack selects on it through a model
 * {@code overrides} predicate, which is how that era switches models at all), so the colour needs its
 * own home. It goes in {@code minecraft:dyed_color} — a vanilla component that stores exactly one RGB
 * int, is read by nothing except dyeable armour's own colour handler, and shows no tooltip when asked
 * not to. {@link dev.golem.core.client.Tints} turns it back into a tint at render time, reproducing what
 * 26.1 does natively through the item definition's {@code tints} array.
 */
public final class ModelData {

    private ModelData() {}

    /** Tags a stack with a tint colour used by its model. */
    public static void setColor(ItemStack stack, int color) {
        //? if >=26.1 {
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(), List.of(color)));
        //?} else {
        /*stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, false));
        *///?}
    }

    /** Tags a stack with a tint colour plus a boolean flag (used for the molten-scrap model). */
    public static void setColorAndFlag(ItemStack stack, int color, boolean flag) {
        //? if >=26.1 {
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(flag), List.of(), List.of(color)));
        //?} else {
        /*// The flag keeps the integer index, which the model's overrides predicate selects on; the
        // colour goes to its own component so neither has to give way to the other.
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(flag ? 1 : 0));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, false));
        *///?}
    }

    /** Reads back the boolean flag set by {@link #setColorAndFlag}. */
    public static boolean flag(ItemStack stack) {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (data == null) return false;
        //? if >=26.1 {
        return !data.flags().isEmpty() && Boolean.TRUE.equals(data.flags().get(0));
        //?} else {
        /*return data.value() == 1;
        *///?}
    }

    /**
     * Reads back the colour set by {@link #setColor}, or {@code defaultColor} if the stack carries none.
     * Both eras answer from wherever that era stored it.
     */
    public static int color(ItemStack stack, int defaultColor) {
        //? if >=26.1 {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null || data.colors().isEmpty() ? defaultColor : data.colors().get(0);
        //?} else {
        /*DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        return dyed == null ? defaultColor : dyed.rgb();
        *///?}
    }
}
