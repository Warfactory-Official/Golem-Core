package dev.golem.core.data;

import net.minecraft.world.item.ItemStack;

//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;
//?}

//? if >=1.20.5 && <26.1 {
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

    // 1.20.1 has no data components. Keep the colour separate so CustomModelData remains the resource
    // pack predicate used by the molten-scrap flag.
    private static final String LEGACY_COLOR = "GolemCoreModelColor";

    /** Tags a stack with a tint colour used by its model. */
    public static void setColor(ItemStack stack, int color) {
        //? if >=26.1 {
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(), List.of(color)));
        //?} elif >=1.20.5 {
        /*stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, false));
        *///?} else {
        /*stack.getOrCreateTag().putInt(LEGACY_COLOR, color);
        *///?}
    }

    // Every colour of a multi-colour stack, below 26.1 (index 0 also stays where setColor puts it).
    private static final String LEGACY_COLORS = "GolemCoreModelColors";

    /**
     * Tags a stack with several tint colours: a custom-model-data tint of index {@code n} reads
     * {@code colors[n]}. 26.1 keeps them as the component's colour list; older versions have one colour
     * slot, so index 0 goes there as {@link #setColor} would put it and the whole list rides custom data.
     */
    public static void setColors(ItemStack stack, int... colors) {
        //? if >=26.1 {
        List<Integer> list = new java.util.ArrayList<>(colors.length);
        for (int color : colors) list.add(color);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(), list));
        //?} elif >=1.20.5 {
        /*if (colors.length > 0) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(colors[0], false));
        net.minecraft.world.item.component.CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putIntArray(LEGACY_COLORS, colors));
        *///?} else {
        /*if (colors.length > 0) stack.getOrCreateTag().putInt(LEGACY_COLOR, colors[0]);
        stack.getOrCreateTag().putIntArray(LEGACY_COLORS, colors);
        *///?}
    }

    /** The colour at {@code index} set by {@link #setColors} (or {@link #setColor} for index 0), or the default. */
    public static int color(ItemStack stack, int index, int defaultColor) {
        //? if >=26.1 {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null || data.colors().size() <= index ? defaultColor : data.colors().get(index);
        //?} elif >=1.20.5 {
        /*if (index == 0) return color(stack, defaultColor);
        net.minecraft.world.item.component.CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return defaultColor;
        int[] colors = custom.copyTag().getIntArray(LEGACY_COLORS);
        return colors.length > index ? colors[index] : defaultColor;
        *///?} else {
        /*if (index == 0) return color(stack, defaultColor);
        if (!stack.hasTag()) return defaultColor;
        int[] colors = stack.getTag().getIntArray(LEGACY_COLORS);
        return colors.length > index ? colors[index] : defaultColor;
        *///?}
    }

    /** Removes the tint and model data created by {@link #setColor}. */
    public static void removeColor(ItemStack stack) {
        //? if >=26.1 {
        stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        //?} elif >=1.20.5 {
        /*stack.remove(DataComponents.DYED_COLOR);
        *///?} else {
        /*if (stack.hasTag()) stack.getTag().remove(LEGACY_COLOR);
        *///?}
    }

    /** Tags a stack with a tint colour plus a boolean flag (used for the molten-scrap model). */
    public static void setColorAndFlag(ItemStack stack, int color, boolean flag) {
        //? if >=26.1 {
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(flag), List.of(), List.of(color)));
        //?} elif >=1.20.5 {
        /*// The flag keeps the integer index, which the model's overrides predicate selects on; the
        // colour goes to its own component so neither has to give way to the other.
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(flag ? 1 : 0));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, false));
        *///?} else {
        /*stack.getOrCreateTag().putInt("CustomModelData", flag ? 1 : 0);
        stack.getOrCreateTag().putInt(LEGACY_COLOR, color);
        *///?}
    }

    /** Reads back the boolean flag set by {@link #setColorAndFlag}. */
    public static boolean flag(ItemStack stack) {
        //? if >=26.1 {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (data == null) return false;
        return !data.flags().isEmpty() && Boolean.TRUE.equals(data.flags().get(0));
        //?} elif >=1.20.5 {
        /*CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return data != null && data.value() == 1;
        *///?} else {
        /*return stack.hasTag() && stack.getTag().getInt("CustomModelData") == 1;
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
        //?} elif >=1.20.5 {
        /*DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        return dyed == null ? defaultColor : dyed.rgb();
        *///?} else {
        /*return stack.hasTag() && stack.getTag().contains(LEGACY_COLOR, 3)
                ? stack.getTag().getInt(LEGACY_COLOR) : defaultColor;
        *///?}
    }
}
