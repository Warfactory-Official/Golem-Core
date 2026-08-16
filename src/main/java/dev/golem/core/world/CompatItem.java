package dev.golem.core.world;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** An {@link Item} with one tooltip override for every supported Minecraft version. */
public class CompatItem extends Item {

    public CompatItem(Properties properties) {
        super(properties);
    }

    //? if forge {
    /*@Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        dev.golem.core.render.ForgeItemRenderers.initialize(consumer);
    }
    *///?}

    /** Adds this item's custom tooltip lines after vanilla's lines. */
    protected void appendCompatHoverText(ItemStack stack, CompatTooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
    }

    /** Handles use with a stable return type across the supported vanilla APIs. */
    protected InteractionResult compatUse(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    /** Receives server-side inventory ticks with the optional equipped slot when vanilla provides it. */
    protected void compatInventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level,
                                       Entity owner, EquipmentSlot slot) {
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

    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level, Entity owner,
                              EquipmentSlot slot) {
        compatInventoryTick(stack, level, owner, slot);
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

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity owner, int slot, boolean selected) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            compatInventoryTick(stack, serverLevel, owner, null);
        }
    }
    *///?}
}
