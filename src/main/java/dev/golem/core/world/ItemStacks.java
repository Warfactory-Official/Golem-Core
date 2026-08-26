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
        return food(nutrition, saturationModifier, false);
    }

    /** Creates food properties, optionally allowing consumption at a full hunger bar. */
    public static FoodProperties food(int nutrition, float saturationModifier, boolean alwaysEdible) {
        //? if >=1.20.5 {
        FoodProperties.Builder builder = new FoodProperties.Builder().nutrition(nutrition)
                .saturationModifier(saturationModifier);
        //? if >=1.21.1 {
        if (alwaysEdible) builder.alwaysEdible();
        //?} else {
        /*if (alwaysEdible) builder.alwaysEat();
        *///?}
        return builder.build();
        //?} else {
        /*FoodProperties.Builder builder = new FoodProperties.Builder().nutrition(nutrition)
                .saturationMod(saturationModifier);
        if (alwaysEdible) builder.alwaysEat();
        return builder.build();
        *///?}
    }

    /** Returns the nutrition value across the pre-record and record-shaped food APIs. */
    public static int foodNutrition(FoodProperties food) {
        //? if >=1.21 {
        return food.nutrition();
        //?} else {
        /*return food.getNutrition();
        *///?}
    }

    /** Returns the constructor-level saturation modifier, not the computed saturation amount. */
    public static float foodSaturationModifier(FoodProperties food) {
        //? if >=1.21 {
        return food.nutrition() == 0 ? 0F : food.saturation() / (food.nutrition() * 2F);
        //?} else {
        /*return food.getSaturationModifier();
        *///?}
    }
}
