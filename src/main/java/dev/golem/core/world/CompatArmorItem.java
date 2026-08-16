package dev.golem.core.world;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A tooltip-compatible armour item base.
 *
 * <p>Pass a {@link GolemArmorMaterial} and an {@link ArmorSlot}; native material construction stays
 * inside Golem Core even though Minecraft moved the material API repeatedly.
 */
public class CompatArmorItem extends
    //? if >=26.1 {
    Item
    //?} else {
    /*net.minecraft.world.item.ArmorItem
    *///?}
{

    @SuppressWarnings("unchecked")
    public CompatArmorItem(GolemArmorMaterial material, ArmorSlot slot, Item.Properties properties) {
        //? if >=26.1 {
        super(configure((net.minecraft.world.item.equipment.ArmorMaterial) material.nativeMaterial(), slot, properties));
        //?} else if >=1.21.1 {
        /*super((net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial>) material.nativeMaterial(), type(slot), properties);
        *///?} else {
        /*super((net.minecraft.world.item.ArmorMaterial) material.nativeMaterial(), type(slot), properties);
        *///?}
    }

    /** Adds this armour item's custom tooltip lines after vanilla's lines. */
    protected void appendCompatHoverText(ItemStack stack, CompatTooltipContext context,
                                         List<Component> tooltip, TooltipFlag flag) {
    }

    /** Handles use with a stable return type across the supported vanilla APIs. */
    protected InteractionResult compatUse(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    //? if >=26.1 {
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        appendCompatHoverText(stack, CompatTooltipContext.INSTANCE, new java.util.AbstractList<>() {
            @Override public Component get(int index) { throw new UnsupportedOperationException(); }
            @Override public int size() { return 0; }
            @Override public void add(int index, Component component) { tooltip.accept(component); }
        }, flag);
    }
    //?} else if >=1.21.1 {
    /*@Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        appendCompatHoverText(stack, CompatTooltipContext.INSTANCE, tooltip, flag);
    }
    *///?} else {
    /*@Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        appendCompatHoverText(stack, CompatTooltipContext.INSTANCE, tooltip, flag);
    }
    *///?}

    //? if >=26.1 {
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return compatUse(level, player, hand);
    }
    //?} else {
    /*@Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                                        InteractionHand hand) {
        InteractionResult result = compatUse(level, player, hand);
        ItemStack held = player.getItemInHand(hand);
        return result == InteractionResult.SUCCESS ? net.minecraft.world.InteractionResultHolder.success(held)
                : result == InteractionResult.CONSUME ? net.minecraft.world.InteractionResultHolder.consume(held)
                : net.minecraft.world.InteractionResultHolder.pass(held);
    }
    *///?}

    //? if >=26.1 {
    private static Item.Properties configure(net.minecraft.world.item.equipment.ArmorMaterial material,
                                             ArmorSlot slot, Item.Properties properties) {
        return properties.humanoidArmor(material, switch (slot) {
            case HELMET -> net.minecraft.world.item.equipment.ArmorType.HELMET;
            case CHESTPLATE -> net.minecraft.world.item.equipment.ArmorType.CHESTPLATE;
            case LEGGINGS -> net.minecraft.world.item.equipment.ArmorType.LEGGINGS;
            case BOOTS -> net.minecraft.world.item.equipment.ArmorType.BOOTS;
        });
    }
    //?} else {
    /*private static net.minecraft.world.item.ArmorItem.Type type(ArmorSlot slot) {
        return switch (slot) {
            case HELMET -> net.minecraft.world.item.ArmorItem.Type.HELMET;
            case CHESTPLATE -> net.minecraft.world.item.ArmorItem.Type.CHESTPLATE;
            case LEGGINGS -> net.minecraft.world.item.ArmorItem.Type.LEGGINGS;
            case BOOTS -> net.minecraft.world.item.ArmorItem.Type.BOOTS;
        };
    }
    *///?}
}
