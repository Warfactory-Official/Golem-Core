package dev.golem.core.world;

import dev.golem.core.Ids;
import dev.golem.core.data.GolemComponent;

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

import java.util.List;

/** Version-neutral operations on item stacks whose vanilla APIs changed between supported versions. */
public final class ItemStacks {

    private ItemStacks() {}

    /**
     * A codec for one stack that tolerates an empty one, for storing a stack inside something else.
     *
     * <p>1.20.5 renamed the tolerant form to {@code OPTIONAL_CODEC} when components landed; before
     * that the plain {@code CODEC} is already the nullable one, and there is no second constant.
     */
    public static com.mojang.serialization.Codec<ItemStack> optionalCodec() {
        //? if >=1.20.5 {
        return ItemStack.OPTIONAL_CODEC;
        //?} else {
        /*return ItemStack.CODEC;
        *///?}
    }

    /** Returns whether both stacks have the same item and item data, ignoring their counts. */
    public static boolean sameItemAndData(ItemStack first, ItemStack second) {
        //? if >=1.20.5 {
        return ItemStack.isSameItemSameComponents(first, second);
        //?} else {
        /*return ItemStack.isSameItemSameTags(first, second);
        *///?}
    }

    /**
     * A hash consistent with {@link #sameItemAndData}: the item and its data, never the count. Same
     * guard as {@code ItemContents.hashCode}: 1.20.5 has components but only 1.21.1 hashes them itself.
     */
    public static int hashItemAndData(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        //? if >=1.21.1 {
        return ItemStack.hashItemAndComponents(stack);
        //?} else {
        /*return java.util.Objects.hash(stack.getItem(), stack.getTag());
        *///?}
    }

    /** Compares item data while ignoring durability, matching the old wildcard filter. */
    public static boolean sameItemAndDataIgnoringDamage(ItemStack first, ItemStack second) {
        //? if >=1.20.5 {
        ItemStack firstCopy = first.copy();
        ItemStack secondCopy = second.copy();
        firstCopy.remove(net.minecraft.core.component.DataComponents.DAMAGE);
        secondCopy.remove(net.minecraft.core.component.DataComponents.DAMAGE);
        return ItemStack.isSameItemSameComponents(firstCopy, secondCopy);
        //?} else {
        /*// Damage is an NBT tag here; isSameItemSameTags would compare it, so it is stripped from copies.
        ItemStack firstCopy = first.copy();
        ItemStack secondCopy = second.copy();
        if (firstCopy.hasTag()) firstCopy.getTag().remove("Damage");
        if (secondCopy.hasTag()) secondCopy.getTag().remove("Damage");
        if (firstCopy.hasTag() && firstCopy.getTag().isEmpty()) firstCopy.setTag(null);
        if (secondCopy.hasTag() && secondCopy.getTag().isEmpty()) secondCopy.setTag(null);
        return ItemStack.isSameItem(firstCopy, secondCopy)
                && ItemStack.isSameItemSameTags(firstCopy, secondCopy);
        *///?}
    }

    /** Returns whether a stack carries only the metadata and damage used by a flattened item family. */
    public static boolean hasOnlyVariantAndDamage(ItemStack stack, GolemComponent<?> variant) {
        //? if >=1.20.5 {
        for (var component : stack.getComponentsPatch().entrySet()) {
            if (component.getKey() != variant.type()
                    && component.getKey() != net.minecraft.core.component.DataComponents.DAMAGE) {
                return false;
            }
        }
        return true;
        //?} else {
        /*return true;
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

    /** Tests that a named item component is absent on the stack. */
    public static boolean matchesAbsentComponent(ItemStack stack, String key) {
        //? if >=1.21.1 {
        net.minecraft.core.component.DataComponentType<?> type =
                net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Ids.parse(key));
        return type != null && !stack.has(type);
        //?} else {
        /*return !stack.hasTag() || !stack.getTag().contains(key);
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

    /** Applies the vanilla custom name and lore used by machines that print a message stack. */
    public static void setNameAndLore(ItemStack stack, String name, List<String> lines) {
        //? if >=1.20.5 {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal(name));
        stack.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(lines.stream()
                        .<net.minecraft.network.chat.Component>map(line ->
                                net.minecraft.network.chat.Component.literal(line)
                                        .withStyle(net.minecraft.ChatFormatting.GRAY))
                        .toList()));
        //?} else {
        /*net.minecraft.nbt.CompoundTag display = stack.getOrCreateTagElement("display");
        net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
        // 1.20.1 lore entries are JSON components, exactly as the LORE component holds them later.
        for (String line : lines) {
            lore.add(net.minecraft.nbt.StringTag.valueOf(net.minecraft.network.chat.Component.Serializer.toJson(
                    net.minecraft.network.chat.Component.literal(line).withStyle(net.minecraft.ChatFormatting.GRAY))));
        }
        display.put("Lore", lore);
        stack.setHoverName(net.minecraft.network.chat.Component.literal(name));
        *///?}
    }

    /** Returns the plain text of vanilla lore lines, regardless of component or legacy NBT storage. */
    public static List<String> loreLines(ItemStack stack) {
        //? if >=1.20.5 {
        net.minecraft.world.item.component.ItemLore lore =
                stack.get(net.minecraft.core.component.DataComponents.LORE);
        return lore == null ? List.of() : lore.lines().stream()
                .map(net.minecraft.network.chat.Component::getString).toList();
        //?} else {
        /*if (!stack.hasTag()) return List.of();
        net.minecraft.nbt.CompoundTag display = stack.getTag().getCompound("display");
        if (!display.contains("Lore", 9)) return List.of();
        net.minecraft.nbt.ListTag lore = display.getList("Lore", 8);
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < lore.size(); i++) {
            net.minecraft.network.chat.Component line =
                    net.minecraft.network.chat.Component.Serializer.fromJson(lore.getString(i));
            lines.add(line == null ? lore.getString(i) : line.getString());
        }
        return List.copyOf(lines);
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

    /**
     * Applies a stack's stored block-entity data to a block entity the item did not place directly. Vanilla
     * applies it at the clicked position, which a folded multiblock has already removed by the time its real
     * core exists elsewhere. Below 1.20.5 there are no components and the pre-existing behaviour stands.
     */
    public static void applyComponents(net.minecraft.world.level.block.entity.BlockEntity blockEntity, ItemStack stack) {
        //? if >=1.20.5 {
        blockEntity.applyComponentsFromItemStack(stack);
        //?}
    }

    /**
     * {@code ItemStack.consume(count, player)}: shrinks the stack unless the player is in creative. The
     * method arrived in 1.20.5; before it, this is the vanilla creative check spelled out.
     */
    public static void consume(ItemStack stack, int count, net.minecraft.world.entity.@org.jspecify.annotations.Nullable LivingEntity player) {
        //? if >=1.21 {
        stack.consume(count, player);
        //?} else {
        /*if (player == null || !(player instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().instabuild)) {
            stack.shrink(count);
        }
        *///?}
    }

    /** Every slot carried by an item (the CONTAINER component; the legacy block-entity "Items" list before 1.20.5). */
    public static NonNullList<ItemStack> carriedItems(ItemStack carrier, int slotCount) {
        NonNullList<ItemStack> items = NonNullList.withSize(slotCount, ItemStack.EMPTY);
        //? if >=1.20.5 {
        net.minecraft.world.item.component.ItemContainerContents contents =
                carrier.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (contents != null) contents.copyInto(items);
        //?} else {
        /*net.minecraft.nbt.CompoundTag data = legacyCarriedData(carrier);
        if (data.contains("Items", 9)) net.minecraft.world.ContainerHelper.loadAllItems(data, items);
        *///?}
        return items;
    }

    /** Packs {@code items} onto the carrier the way {@link #carriedItems} reads them. */
    public static void setCarriedItems(ItemStack carrier, List<ItemStack> items) {
        //? if >=1.20.5 {
        carrier.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
        //?} else {
        /*NonNullList<ItemStack> list = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) list.set(i, items.get(i));
        net.minecraft.nbt.CompoundTag data = carrier.getOrCreateTagElement("BlockEntityTag");
        net.minecraft.world.ContainerHelper.saveAllItems(data, list);
        *///?}
    }

    /** Sets the stack's display name (CUSTOM_NAME component / legacy display tag). */
    public static void setCustomName(ItemStack stack, net.minecraft.network.chat.Component name) {
        //? if >=1.20.5 {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
        //?} else {
        /*stack.setHoverName(name);
        *///?}
    }

    /** The compressed NBT size of a stack, for 1.7-style "too big to drop" guards. */
    public static int compressedSize(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        try {
            //? if >=1.20.5 {
            net.minecraft.nbt.Tag encoded = ItemStack.CODEC.encodeStart(
                    net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, registries), stack)
                    .result().orElse(null);
            if (!(encoded instanceof net.minecraft.nbt.CompoundTag tag)) return 0;
            //?} else {
            /*net.minecraft.nbt.CompoundTag tag = stack.save(new net.minecraft.nbt.CompoundTag());
            *///?}
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.writeCompressed(tag, out);
            return out.size();
        } catch (java.io.IOException ignored) {
            return 0;
        }
    }

    /** The stack's custom display name, or null (CUSTOM_NAME component / legacy display tag). */
    public static net.minecraft.network.chat.@org.jspecify.annotations.Nullable Component customName(ItemStack stack) {
        //? if >=1.20.5 {
        return stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        //?} else {
        /*return stack.hasCustomHoverName() ? stack.getHoverName() : null;
        *///?}
    }

    /** Caps the stack at {@code max} (1.20.5 {@code limitSize}). */
    public static void limitSize(ItemStack stack, int max) {
        if (stack.getCount() > max) stack.setCount(max);
    }
}
