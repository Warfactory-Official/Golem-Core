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

    /**
     * A copy of {@code template} carrying the data components named in {@code components}, written in
     * the 1.20.5+ item-stack JSON spelling ({@code {"hbm:fluid": {...}, "minecraft:damage": 60}}), or
     * empty when they do not decode.
     *
     * <p>Below 1.20.5 there are no components: each non-vanilla key becomes the root NBT key that
     * {@link GolemComponent} stores it under, {@code minecraft:damage} becomes the damage value, and any
     * other {@code minecraft:} component (which has no NBT counterpart there) is skipped. The old
     * {@code ItemStack.CODEC} cannot parse this spelling: it fails on the missing {@code Count} field.
     */
    public static java.util.Optional<ItemStack> withComponents(ItemStack template, com.google.gson.JsonObject components,
            com.mojang.serialization.DynamicOps<com.google.gson.JsonElement> ops) {
        //? if >=1.20.5 {
        com.google.gson.JsonObject stack = new com.google.gson.JsonObject();
        stack.addProperty("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(template.getItem()).toString());
        stack.addProperty("count", template.getCount());
        stack.add("components", components);
        return ItemStack.OPTIONAL_CODEC.parse(ops, stack).result();
        //?} else {
        /*ItemStack copy = template.copy();
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> component : components.entrySet()) {
            String key = component.getKey();
            if (key.equals("minecraft:damage")) {
                if (!component.getValue().isJsonPrimitive()) return java.util.Optional.empty();
                copy.setDamageValue(component.getValue().getAsInt());
            } else if (!key.startsWith("minecraft:")) {
                copy.getOrCreateTag().put(key, com.mojang.serialization.JsonOps.INSTANCE.convertTo(
                        net.minecraft.nbt.NbtOps.INSTANCE, component.getValue()));
            }
        }
        return java.util.Optional.of(copy);
        *///?}
    }

    /**
     * Items by registry name as the newest API spells it, on every node: {@code "minecraft:short_grass"} decodes to the
     * node's own item ({@link Ids#nodeItemId}) and every item encodes to its newest-API name ({@link Ids#canonicalItemId}).
     * An unknown id is an error everywhere, as on 26.1: 1.20.1's defaulted-registry {@code byNameCodec} silently decodes it
     * to air instead.
     */
    public static com.mojang.serialization.Codec<net.minecraft.world.item.Item> itemCodec() {
        return com.mojang.serialization.Codec.STRING.comapFlatMap(id -> {
            String node;
            try {
                node = Ids.parse(id).toString();
            } catch (RuntimeException e) {
                return com.mojang.serialization.DataResult.error(() -> "Not a valid item id: " + id);
            }
            node = Ids.nodeItemId(node);
            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(Ids.parse(node))) {
                return com.mojang.serialization.DataResult.error(() -> "Unknown item: " + id);
            }
            return com.mojang.serialization.DataResult.success(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Ids.parse(node)));
        }, item -> Ids.canonicalItemId(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString()));
    }

    /**
     * {@code stack} in the newest API's item-stack JSON spelling on every node: {@code {"id", "count", "components"}}, the id
     * as {@link Ids#canonicalItemId} names it, the component keys sorted, {@code "components"} left out when there are none,
     * {@code {}} for an empty stack. For text that must read the same on every node (a GameTest's dump); never parse it
     * back. Below 1.20.5 a {@link GolemComponent}'s value is re-encoded with its codec (1.20.1 keeps a boolean as a byte),
     * the damage value is {@code minecraft:damage} when not zero, and any other NBT key is converted as it is.
     */
    public static com.google.gson.JsonObject modernJson(ItemStack stack, com.mojang.serialization.DynamicOps<com.google.gson.JsonElement> ops) {
        com.google.gson.JsonObject out = new com.google.gson.JsonObject();
        if (stack.isEmpty()) return out;
        java.util.TreeMap<String, com.google.gson.JsonElement> components = new java.util.TreeMap<>();
        //? if >=1.20.5 {
        com.google.gson.JsonElement encoded = ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).result().orElse(null);
        if (encoded != null && encoded.isJsonObject() && encoded.getAsJsonObject().get("components") instanceof com.google.gson.JsonObject patch) {
            for (var entry : patch.entrySet()) components.put(entry.getKey(), entry.getValue());
        }
        //?} else {
        /*net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag != null) {
            for (String key : tag.getAllKeys()) {
                if (key.equals("Damage")) continue;
                components.put(key, GolemComponent.legacyJson(key, tag.get(key)).orElseGet(() ->
                        net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE, tag.get(key))));
            }
        }
        if (stack.getDamageValue() != 0) components.put("minecraft:damage", new com.google.gson.JsonPrimitive(stack.getDamageValue()));
        *///?}
        out.addProperty("id", Ids.canonicalItemId(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        out.addProperty("count", stack.getCount());
        if (!components.isEmpty()) {
            com.google.gson.JsonObject sorted = new com.google.gson.JsonObject();
            components.forEach(sorted::add);
            out.add("components", sorted);
        }
        return out;
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

    /**
     * Returns whether a stack carries nothing but damage and the given components (a mod's typed stand-ins for its old
     * item metadata) - the modern spelling of "no NBT".
     */
    public static boolean hasOnlyVariantAndDamage(ItemStack stack, GolemComponent<?>... allowed) {
        //? if >=1.20.5 {
        outer:
        for (var component : stack.getComponentsPatch().entrySet()) {
            if (component.getKey() == net.minecraft.core.component.DataComponents.DAMAGE) continue;
            for (GolemComponent<?> variant : allowed) {
                if (component.getKey() == variant.type()) continue outer;
            }
            return false;
        }
        return true;
        //?} else {
        /*var tag = stack.getTag();
        if (tag == null) return true;
        outer:
        for (String name : tag.getAllKeys()) {
            if (name.equals("Damage")) continue;
            for (GolemComponent<?> variant : allowed) {
                if (variant.key().equals(name)) continue outer;
            }
            return false;
        }
        return true;
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

    /**
     * Tests one string-valued component - one whose codec writes a string, such as a registry name - against the
     * string a recipe names, or its namespaced legacy NBT string on 1.20.1. The component's own codec does the
     * comparison, so a typed value ({@code FluidType}) matches the name it is saved as.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean matchesStringComponent(ItemStack stack, String key, String value) {
        //? if >=1.21.1 {
        net.minecraft.core.component.DataComponentType type =
                net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Ids.parse(key));
        if (type == null || !stack.has(type) || type.codec() == null) return false;
        Object held = stack.get(type);
        java.util.Optional<com.google.gson.JsonElement> json = ((com.mojang.serialization.Codec<Object>) type.codec())
                .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, held).result();
        return json.isPresent() && json.get() instanceof com.google.gson.JsonPrimitive primitive && primitive.isString()
                && value.equals(primitive.getAsString());
        //?} else {
        /*return stack.hasTag() && stack.getTag().contains(key, 8) && stack.getTag().getString(key).equals(value);
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

    /**
     * Whether the stack, held in the main hand, carries an attack-damage attribute modifier - legacy
     * {@code stack.getAttributeModifiers().containsKey(attackDamage)}, i.e. "is this a weapon or tool".
     */
    public static boolean hasAttackDamageModifier(ItemStack stack) {
        if (stack.isEmpty()) return false;
        //? if >=1.21 {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                        net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream()
                .anyMatch(entry -> entry.attribute().value()
                        == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE.value());
        //?} else {
        /*return stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .containsKey(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        *///?}
    }

    /**
     * Strips every attribute modifier from this one stack ({@code suppressed}), or gives it back its item's own
     * modifiers - a legacy per-stack {@code getAttributeModifiers(stack)} override that returned an empty map in some
     * states (an out-of-charge weapon). 1.20.5+ overrides the {@code attribute_modifiers} component on the stack
     * (setting it back to the item's default value drops the override from the patch); before that a present, empty
     * {@code AttributeModifiers} tag list replaces the item's modifiers, and removing the tag restores them.
     */
    public static void suppressAttributeModifiers(ItemStack stack, boolean suppressed) {
        if (stack.isEmpty()) return;
        //? if >=1.20.5 {
        var type = net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS;
        if (suppressed) {
            stack.set(type, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        } else {
            var def = stack.getItem().components().get(type);
            if (def != null) stack.set(type, def); else stack.remove(type);
        }
        //?} else {
        /*if (suppressed) {
            stack.getOrCreateTag().put("AttributeModifiers", new net.minecraft.nbt.ListTag());
        } else if (stack.getTag() != null) {
            stack.getTag().remove("AttributeModifiers");
        }
        *///?}
    }

    /** Whether {@link #suppressAttributeModifiers} currently holds this stack's modifiers off. */
    public static boolean attributeModifiersSuppressed(ItemStack stack) {
        //? if >=1.20.5 {
        var value = stack.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
        return value != null && value.modifiers().isEmpty()
                && !value.equals(stack.getItem().components().get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS));
        //?} else {
        /*return stack.getTag() != null && stack.getTag().contains("AttributeModifiers", 9)
                && stack.getTag().getList("AttributeModifiers", 10).isEmpty();
        *///?}
    }

    /**
     * Gives this one stack its own main-hand weapon modifiers - a legacy per-stack {@code getAttributeModifiers(slot,
     * stack)} override that returned other numbers in some states (the 1.12 crucible: 500 damage charged, 5 empty).
     * {@code attackDamage} and {@code attackSpeed} are absolute additions, {@code movement} a base multiplier (0: none).
     * {@link #suppressAttributeModifiers} with {@code false} gives the item's own modifiers back.
     */
    public static void overrideMainHandAttributes(ItemStack stack, float attackDamage, float attackSpeed, double movement) {
        if (stack.isEmpty()) return;
        // >=1.21.1, not the >=1.20.5 of the component's other users: it follows Tools.attributes, whose
        // id-keyed AttributeModifier is 1.21; 1.20.5-1.21.0 would need a third (UUID + component) branch.
        //? if >=1.21.1 {
        stack.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                Tools.attributes(attackDamage, attackSpeed, movement));
        //?} else {
        /*if (stack.getTag() != null) stack.getTag().remove("AttributeModifiers");
        Tools.legacyAttributes(java.util.UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF"),
                java.util.UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3"), attackDamage, attackSpeed, movement)
                .forEach((attribute, modifier) -> stack.addAttributeModifier(attribute, modifier, EquipmentSlot.MAINHAND));
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

    /** Reads a string from an item's carried block custom data; empty when absent or not a string. */
    public static String carriedString(ItemStack carrier, String key) {
        //? if >=1.20.5 {
        net.minecraft.world.item.component.CustomData data =
                carrier.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? "" : dev.golem.core.data.Tags.getStringOr(data.copyTag(), key, "");
        //?} else {
        /*return dev.golem.core.data.Tags.getStringOr(legacyCarriedData(carrier), key, "");
        *///?}
    }

    /** Writes a boolean into an item's carried block custom data, where {@link #carriedBoolean} reads it; false removes it. */
    public static void setCarriedBoolean(ItemStack carrier, String key, boolean value) {
        if (!value) {
            removeCarried(carrier, key);
            return;
        }
        //? if >=1.20.5 {
        net.minecraft.world.item.component.CustomData data =
                carrier.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag tag = data == null ? new net.minecraft.nbt.CompoundTag() : data.copyTag();
        tag.putBoolean(key, true);
        carrier.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        //?} else {
        /*carrier.getOrCreateTagElement("BlockEntityTag").putBoolean(key, true);
        *///?}
    }

    /** Writes a long into an item's carried block custom data, where {@link #carriedLong} reads it. */
    public static void setCarriedLong(ItemStack carrier, String key, long value) {
        //? if >=1.20.5 {
        net.minecraft.world.item.component.CustomData data =
                carrier.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag tag = data == null ? new net.minecraft.nbt.CompoundTag() : data.copyTag();
        tag.putLong(key, value);
        carrier.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        //?} else {
        /*carrier.getOrCreateTagElement("BlockEntityTag").putLong(key, value);
        *///?}
    }

    /** Reads a long from an item's carried block custom data. */
    public static long carriedLong(ItemStack carrier, String key) {
        //? if >=1.20.5 {
        net.minecraft.world.item.component.CustomData data =
                carrier.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? 0L : dev.golem.core.data.Tags.getLongOr(data.copyTag(), key, 0L);
        //?} else {
        /*return legacyCarriedData(carrier).getLong(key);
        *///?}
    }

    /** Removes a key from an item's carried block custom data, and the data itself once it is empty. */
    public static void removeCarried(ItemStack carrier, String key) {
        //? if >=1.20.5 {
        net.minecraft.world.item.component.CustomData data =
                carrier.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return;
        net.minecraft.nbt.CompoundTag tag = data.copyTag();
        tag.remove(key);
        if (tag.isEmpty()) carrier.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        else carrier.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        //?} else {
        /*net.minecraft.nbt.CompoundTag root = carrier.getTag();
        if (root == null) return;
        net.minecraft.nbt.CompoundTag data = root.getCompound("BlockEntityTag");
        data.remove(key);
        data.getCompound(dev.golem.core.data.CompatBlockEntity.LEGACY_CARRIED_KEY).remove(key);
        dropEmptyCarriedData(carrier);
        *///?}
    }

    /** Whether a stack differs from a fresh stack of its item (any component patch; any tag before 1.20.5). */
    public static boolean isPlain(ItemStack stack) {
        //? if >=1.20.5 {
        return stack.getComponentsPatch().isEmpty();
        //?} else {
        /*return !stack.hasTag();
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
        if (!root.contains("BlockEntityTag", 10)) return root;
        net.minecraft.nbt.CompoundTag data = root.getCompound("BlockEntityTag");
        String carried = dev.golem.core.data.CompatBlockEntity.LEGACY_CARRIED_KEY;
        // a mined block's collectCarriedData() arrives nested (loot copy_nbt); read it like the flat keys
        return data.contains(carried, 10) ? data.copy().merge(data.getCompound(carried)) : data;
    }

    /^* Drops carried-data compounds left empty, and the tag itself, so the stack stacks with a fresh one again. ^/
    private static void dropEmptyCarriedData(ItemStack carrier) {
        net.minecraft.nbt.CompoundTag root = carrier.getTag();
        if (root == null) return;
        if (root.contains("BlockEntityTag", 10)) {
            net.minecraft.nbt.CompoundTag data = root.getCompound("BlockEntityTag");
            String carried = dev.golem.core.data.CompatBlockEntity.LEGACY_CARRIED_KEY;
            if (data.contains(carried, 10) && data.getCompound(carried).isEmpty()) data.remove(carried);
            if (data.isEmpty()) root.remove("BlockEntityTag");
        }
        if (root.isEmpty()) carrier.setTag(null);
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
     * core exists elsewhere. Below 1.20.5 the same data is the item's {@code BlockEntityTag}, merged into the block
     * entity exactly as vanilla's {@code BlockItem.updateCustomBlockEntityTag} does at the clicked position.
     */
    public static void applyComponents(net.minecraft.world.level.block.entity.BlockEntity blockEntity, ItemStack stack) {
        //? if >=1.20.5 {
        blockEntity.applyComponentsFromItemStack(stack);
        //?} else {
        /*net.minecraft.nbt.CompoundTag data = net.minecraft.world.item.BlockItem.getBlockEntityData(stack);
        if (data == null) return;
        net.minecraft.nbt.CompoundTag current = blockEntity.saveWithoutMetadata();
        net.minecraft.nbt.CompoundTag before = current.copy();
        current.merge(data);
        if (!current.equals(before)) {
            blockEntity.load(current);
            blockEntity.setChanged();
        }
        *///?}
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

    /** Whether {@code stack} may go into an item store: vanilla's shulker-box rule, and nothing that carries items. */
    public static boolean fitsInContainerItem(ItemStack stack) {
        if (!stack.getItem().canFitInsideContainerItems()) return false;
        //? if >=1.20.5 {
        net.minecraft.world.item.component.BundleContents bundle =
                stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
        return (bundle == null || bundle.isEmpty())
                && stack.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
                        net.minecraft.world.item.component.ItemContainerContents.EMPTY)
                        .equals(net.minecraft.world.item.component.ItemContainerContents.EMPTY);
        //?} else {
        /*net.minecraft.nbt.CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains("Items", 9) && !legacyCarriedData(stack).contains("Items", 9);
        *///?}
    }

    /**
     * Every slot a block entity's item form carries, for block entities that save their inventory under a
     * child key ({@code legacyInventoryKey}, vanilla's {@code "Items"} list inside it): the CONTAINER component
     * from 1.20.5, the {@code BlockEntityTag.<key>} compound before it.
     */
    public static NonNullList<ItemStack> carriedItems(ItemStack carrier, String legacyInventoryKey, int slotCount) {
        //? if >=1.20.5 {
        return carriedItems(carrier, slotCount);
        //?} else {
        /*NonNullList<ItemStack> items = NonNullList.withSize(slotCount, ItemStack.EMPTY);
        net.minecraft.nbt.CompoundTag data = legacyCarriedData(carrier);
        if (data.contains(legacyInventoryKey, 10)) {
            net.minecraft.world.ContainerHelper.loadAllItems(data.getCompound(legacyInventoryKey), items);
        }
        return items;
        *///?}
    }

    /**
     * Writes {@code items} the way {@link #carriedItems(ItemStack, String, int)} reads them. All-empty items leave
     * no inventory on the carrier, so an emptied carrier stacks with a fresh one.
     */
    public static void setCarriedItems(ItemStack carrier, String legacyInventoryKey, List<ItemStack> items) {
        boolean empty = items.stream().allMatch(ItemStack::isEmpty);
        //? if >=1.20.5 {
        if (!empty) {
            setCarriedItems(carrier, items);
        } else if (carrier.getItem().components().has(net.minecraft.core.component.DataComponents.CONTAINER)) {
            carrier.set(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.EMPTY);
        } else {
            carrier.remove(net.minecraft.core.component.DataComponents.CONTAINER);
        }
        //?} else {
        /*if (empty) {
            net.minecraft.nbt.CompoundTag root = carrier.getTag();
            if (root == null) return;
            root.getCompound("BlockEntityTag").remove(legacyInventoryKey);
            dropEmptyCarriedData(carrier);
            return;
        }
        NonNullList<ItemStack> list = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) list.set(i, items.get(i));
        net.minecraft.nbt.CompoundTag data = carrier.getOrCreateTagElement("BlockEntityTag");
        net.minecraft.nbt.CompoundTag inventory = new net.minecraft.nbt.CompoundTag();
        net.minecraft.world.ContainerHelper.saveAllItems(inventory, list, true);
        data.put(legacyInventoryKey, inventory);
        *///?}
    }

    /** A book and quill holding {@code pages} (WRITABLE_BOOK_CONTENT from 1.20.5, the "pages" string list before). */
    public static ItemStack writableBook(List<String> pages) {
        ItemStack book = new ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK);
        //? if >=1.20.5 {
        List<net.minecraft.server.network.Filterable<String>> list = new java.util.ArrayList<>();
        for (String page : pages) list.add(net.minecraft.server.network.Filterable.passThrough(page));
        book.set(net.minecraft.core.component.DataComponents.WRITABLE_BOOK_CONTENT,
                new net.minecraft.world.item.component.WritableBookContent(list));
        //?} else {
        /*net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (String page : pages) list.add(net.minecraft.nbt.StringTag.valueOf(page));
        book.getOrCreateTag().put("pages", list);
        *///?}
        return book;
    }

    /** The pages of a book and quill; empty when it has none. */
    public static List<String> writableBookPages(ItemStack book) {
        List<String> out = new java.util.ArrayList<>();
        //? if >=1.20.5 {
        net.minecraft.world.item.component.WritableBookContent content =
                book.get(net.minecraft.core.component.DataComponents.WRITABLE_BOOK_CONTENT);
        if (content != null) content.pages().forEach(page -> out.add(page.raw()));
        //?} else {
        /*net.minecraft.nbt.CompoundTag tag = book.getTag();
        if (tag != null) {
            net.minecraft.nbt.ListTag list = tag.getList("pages", 8);
            for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        }
        *///?}
        return out;
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

    /**
     * Gives this one stack its own maximum stack size: 1.20.5's {@code minecraft:max_stack_size} component,
     * the modern spelling of a 1.7 {@code getItemStackLimit(ItemStack)} that answered per stack.
     *
     * <p>Below 1.20.5 there are no components and the vanilla limit is per item, so the size rides on the
     * stack's tag under {@link #LEGACY_MAX_STACK_SIZE} and is read back by the loader's per-stack hook:
     * Forge's {@code IForgeItem.getMaxStackSize(ItemStack)} in {@link CompatItem}, and on Fabric (whose API
     * has no such hook before components) {@code MixinLegacyStackLimit}.
     */
    public static void setMaxStackSize(ItemStack stack, int size) {
        //? if >=1.20.5 {
        stack.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, size);
        //?} else {
        /*stack.getOrCreateTag().putInt(LEGACY_MAX_STACK_SIZE, size);
        *///?}
    }

    /** The tag key {@link #setMaxStackSize} writes below 1.20.5; unused (and never written) from 1.20.5 on. */
    public static final String LEGACY_MAX_STACK_SIZE = "golem:max_stack_size";

    /**
     * The per-stack size {@link #setMaxStackSize} gave a pre-1.20.5 stack, or {@code fallback} (the item's own
     * limit) when it was given none. From 1.20.5 vanilla reads the component itself and this answers
     * {@link ItemStack#getMaxStackSize()}.
     */
    public static int legacyMaxStackSize(ItemStack stack, int fallback) {
        //? if >=1.20.5 {
        return stack.getMaxStackSize();
        //?} else {
        /*net.minecraft.nbt.CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(LEGACY_MAX_STACK_SIZE, net.minecraft.nbt.Tag.TAG_INT)
                ? tag.getInt(LEGACY_MAX_STACK_SIZE) : fallback;
        *///?}
    }

    /** Caps the stack at {@code max} (1.20.5 {@code limitSize}). */
    public static void limitSize(ItemStack stack, int max) {
        if (stack.getCount() > max) stack.setCount(max);
    }

    /** A player head showing {@code player}'s skin (legacy {@code SkullOwner}, modern profile component). */
    public static ItemStack playerHead(Player player) {
        ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        //? if >=26.1 {
        head.set(net.minecraft.core.component.DataComponents.PROFILE,
                net.minecraft.world.item.component.ResolvableProfile.createResolved(player.getGameProfile()));
        //?} else if >=1.21.1 {
        /*head.set(net.minecraft.core.component.DataComponents.PROFILE,
                new net.minecraft.world.item.component.ResolvableProfile(player.getGameProfile()));
        *///?} else {
        /*head.getOrCreateTag().putString("SkullOwner", player.getGameProfile().getName());
        *///?}
        return head;
    }
}
