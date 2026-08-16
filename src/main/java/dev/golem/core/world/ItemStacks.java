package dev.golem.core.world;

//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
//?} else {
/*import net.minecraft.network.FriendlyByteBuf;
*///?}
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/** Version-neutral operations on item stacks whose vanilla APIs changed between supported versions. */
public final class ItemStacks {

    private ItemStacks() {}

    /** Returns whether both stacks have the same item and item data, ignoring their counts. */
    public static boolean sameItemAndData(ItemStack first, ItemStack second) {
        //? if >=1.20.5 {
        return ItemStack.isSameItemSameComponents(first, second);
        //?} else {
        /*return ItemStack.isSameItemSameTags(first, second);
        *///?}
    }

    /** Copies a stack while replacing its count. */
    public static ItemStack copyWithCount(ItemStack stack, int count) {
        //? if >=1.20.5 {
        return stack.copyWithCount(count);
        //?} else {
        /*ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
        *///?}
    }

    /** Encodes a possibly-empty stack using the native format for the target version. */
    public static void writeOptional(
            //? if >=1.20.5 {
            RegistryFriendlyByteBuf
            //?} else {
            /*FriendlyByteBuf
            *///?}
            buffer, ItemStack stack) {
        //? if >=1.20.5 {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
        //?} else {
        /*buffer.writeItem(stack);
        *///?}
    }

    /** Decodes a stack written by {@link #writeOptional}. */
    public static ItemStack readOptional(
            //? if >=1.20.5 {
            RegistryFriendlyByteBuf
            //?} else {
            /*FriendlyByteBuf
            *///?}
            buffer) {
        //? if >=1.20.5 {
        return ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        //?} else {
        /*return buffer.readItem();
        *///?}
    }

    /** Returns the food properties attached to this stack's item, or {@code null} when it is not food. */
    public static FoodProperties foodProperties(ItemStack stack) {
        //? if >=1.21 {
        return stack.get(net.minecraft.core.component.DataComponents.FOOD);
        //?} else {
        /*return stack.getItem().getFoodProperties();
        *///?}
    }

    /** Creates food properties despite the builder's saturation method rename. */
    public static FoodProperties food(int nutrition, float saturationModifier) {
        //? if >=1.20.5 {
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturationModifier).build();
        //?} else {
        /*return new FoodProperties.Builder().nutrition(nutrition).saturationMod(saturationModifier).build();
        *///?}
    }
}
