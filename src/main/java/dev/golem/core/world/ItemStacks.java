package dev.golem.core.world;

import dev.golem.core.Ids;

//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
//?} else {
/*import net.minecraft.network.FriendlyByteBuf;
*///?}
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;

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

    /** Tests an item registry id without exposing the registry lookup spelling to consumers. */
    public static boolean matchesItemId(ItemStack stack, String id) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Ids.parse(id)));
    }

    /** Tests one integer component, or its namespaced legacy NBT representation on 1.20.1. */
    public static boolean matchesIntComponent(ItemStack stack, String key, int value) {
        //? if >=1.21.1 {
        net.minecraft.core.component.DataComponentType<?> type =
                net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Ids.parse(key));
        return type != null && stack.has(type) && Integer.valueOf(value).equals(stack.get(type));
        //?} else {
        /*return stack.hasTag() && stack.getTag().contains(key, 3) && stack.getTag().getInt(key) == value;
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

    /** Damages a held item and broadcasts its break animation across the callback signature change. */
    public static void hurtAndBreak(ItemStack stack, int amount, Player player, InteractionHand hand) {
        //? if >=1.21.1 {
        stack.hurtAndBreak(amount, player,
                hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        //?} else {
        /*stack.hurtAndBreak(amount, player, owner -> owner.broadcastBreakEvent(hand));
        *///?}
    }

    /** Copies the source stack's mutable custom state onto an already-configured output stack. */
    public static void copyCustomState(ItemStack source, ItemStack output) {
        //? if >=1.20.5 {
        output.applyComponents(source.getComponentsPatch());
        //?} else {
        /*if (source.hasTag()) output.getOrCreateTag().merge(source.getTag().copy());
        *///?}
    }

    /** Copies only the user-assigned display name between transformed container items. */
    public static void copyCustomName(ItemStack source, ItemStack output) {
        //? if >=1.20.5 {
        net.minecraft.network.chat.Component name = source.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (name != null) output.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
        //?} else {
        /*if (source.hasCustomHoverName()) output.setHoverName(source.getHoverName());
        *///?}
    }

    /** Reads one slot from block-entity state carried by an item. */
    public static ItemStack carriedItem(ItemStack carrier, String legacyInventoryKey, int slotCount, int slot) {
        if (slot < 0 || slot >= slotCount) return ItemStack.EMPTY;
        //? if >=1.20.5 {
        net.minecraft.world.item.component.ItemContainerContents contents =
                carrier.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (contents == null) return ItemStack.EMPTY;
        NonNullList<ItemStack> items = NonNullList.withSize(slotCount, ItemStack.EMPTY);
        contents.copyInto(items);
        return items.get(slot).copy();
        //?} else {
        /*net.minecraft.nbt.CompoundTag data = legacyCarriedData(carrier);
        if (data.contains(legacyInventoryKey, 10)) {
            NonNullList<ItemStack> items = NonNullList.withSize(slotCount, ItemStack.EMPTY);
            net.minecraft.world.ContainerHelper.loadAllItems(data.getCompound(legacyInventoryKey), items);
            return items.get(slot).copy();
        }
        String directSlot = "slot" + slot;
        return data.contains(directSlot, 10) ? ItemStack.of(data.getCompound(directSlot)) : ItemStack.EMPTY;
        *///?}
    }

    /** Reads an integer from an item's carried block custom data. */
    public static int carriedInt(ItemStack carrier, String key) {
        //? if >=1.20.5 {
        net.minecraft.world.item.component.CustomData data =
                carrier.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? 0 : dev.golem.core.data.Tags.getIntOr(data.copyTag(), key, 0);
        //?} else {
        /*return legacyCarriedData(carrier).getInt(key);
        *///?}
    }

    /** Reads a boolean from an item's carried block custom data. */
    public static boolean carriedBoolean(ItemStack carrier, String key) {
        //? if >=1.20.5 {
        net.minecraft.world.item.component.CustomData data =
                carrier.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data != null && dev.golem.core.data.Tags.getBooleanOr(data.copyTag(), key, false);
        //?} else {
        /*return legacyCarriedData(carrier).getBoolean(key);
        *///?}
    }

    //? if <1.20.5 {
    /*private static net.minecraft.nbt.CompoundTag legacyCarriedData(ItemStack carrier) {
        net.minecraft.nbt.CompoundTag root = carrier.getTag();
        if (root == null) return new net.minecraft.nbt.CompoundTag();
        return root.contains("BlockEntityTag", 10) ? root.getCompound("BlockEntityTag") : root;
    }
    *///?}

    /** Runs the vanilla crafted-item callback despite its level parameter changing across versions. */
    public static void onCraftedBy(ItemStack stack, Player player, int count) {
        //? if >=26.1 {
        stack.onCraftedBy(player, count);
        //?} else {
        /*stack.onCraftedBy(player.level(), player, count);
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
