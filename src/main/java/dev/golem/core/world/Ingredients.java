package dev.golem.core.world;

import dev.golem.core.Ids;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Ingredient predicates that are spelled differently on every supported version: item tags named by
 * string, and the potion a potion-like item carries (a component from 1.20.5, an NBT string before).
 */
public final class Ingredients {

    private Ingredients() {}

    /** Whether the stack is in the item tag {@code id} ({@code "c:ingots/iron"}, no leading {@code #}). */
    public static boolean matchesTag(ItemStack stack, String id) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(TagKey.create(Registries.ITEM, Ids.parse(id)));
    }

    /** The items currently bound to an item tag; empty until tags are loaded. */
    public static List<Item> tagItems(String id) {
        List<Item> items = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, Ids.parse(id)))) {
            items.add(holder.value());
        }
        return items;
    }

    /** The registry id of the potion a potion, splash potion or tipped arrow carries, or {@code ""}. */
    public static String potionId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        //? if >=1.20.5 {
        net.minecraft.world.item.alchemy.PotionContents contents =
                stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (contents == null) return "";
        return contents.potion().map(Holder::getRegisteredName).orElse("");
        //?} else {
        /*if (!stack.hasTag() || !stack.getTag().contains("Potion", 8)) return "";
        net.minecraft.world.item.alchemy.Potion potion = net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack);
        return BuiltInRegistries.POTION.getKey(potion).toString();
        *///?}
    }

    /** One {@code item} carrying the potion {@code id} ({@code minecraft:water} makes a water bottle). */
    public static ItemStack potionStack(String item, String id) {
        Item base = BuiltInRegistries.ITEM.getValue(Ids.parse(item));
        //? if >=26.1 {
        net.minecraft.world.item.alchemy.Potion potion = BuiltInRegistries.POTION.getValue(Ids.parse(id));
        if (potion == null) return new ItemStack(base);
        return net.minecraft.world.item.alchemy.PotionContents.createItemStack(base,
                BuiltInRegistries.POTION.wrapAsHolder(potion));
        //?} else if >=1.20.5 {
        /*return BuiltInRegistries.POTION.getHolder(Ids.parse(id))
                .map(holder -> net.minecraft.world.item.alchemy.PotionContents.createItemStack(base, holder))
                .orElseGet(() -> new ItemStack(base));
        *///?} else {
        /*net.minecraft.world.item.alchemy.Potion potion = BuiltInRegistries.POTION.get(Ids.parse(id));
        return net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(base), potion);
        *///?}
    }

    /**
     * 1.7's crafting-tool rule ({@code ItemCraftingDegradation.getContainerItem} with
     * {@code SlotCrafting}): the tool stays in the grid one point more worn, and is gone once its damage
     * exceeds (not reaches) its maximum. An item without durability stays untouched.
     */
    public static ItemStack degraded(ItemStack stack) {
        ItemStack copy = ItemStacks.copyWithCount(stack, 1);
        int max = copy.getMaxDamage();
        if (max <= 0) return copy;
        int damage = copy.getDamageValue() + 1;
        if (damage > max) return ItemStack.EMPTY;
        copy.setDamageValue(damage);
        return copy;
    }
}
