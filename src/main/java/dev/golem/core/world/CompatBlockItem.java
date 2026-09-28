package dev.golem.core.world;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** A {@link BlockItem} with version-neutral tooltip, pre-placement and air-use hooks. */
public class CompatBlockItem extends BlockItem {

    public CompatBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    //? if forge {
    /*@Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        dev.golem.core.render.ForgeItemRenderers.initialize(consumer);
    }
    *///?}

    protected void appendCompatHoverText(ItemStack stack, CompatTooltipContext context,
                                         List<Component> tooltip, TooltipFlag flag) {
    }

    /**
     * Runs before the item places its block; answer non-null to use that result instead of placing (e.g.
     * {@code InteractionResult.PASS} to hand the click on to {@link #compatUse}).
     */
    protected net.minecraft.world.@org.jspecify.annotations.Nullable InteractionResult compatBeforePlace(
            net.minecraft.world.item.context.UseOnContext context) {
        return null;
    }

    /** Right-click with the item in the air (or after {@link #compatBeforePlace} passed); PASS by default. */
    protected net.minecraft.world.InteractionResult compatUse(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        net.minecraft.world.InteractionResult result = compatBeforePlace(context);
        return result != null ? result : super.useOn(context);
    }

    //? if >=26.1 {
    @Override
    public net.minecraft.world.InteractionResult use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        net.minecraft.world.InteractionResult result = compatUse(level, player, hand);
        return result != net.minecraft.world.InteractionResult.PASS ? result : super.use(level, player, hand);
    }
    //?} else {
    /*@Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        net.minecraft.world.InteractionResult result = compatUse(level, player, hand);
        if (result == net.minecraft.world.InteractionResult.PASS) return super.use(level, player, hand);
        ItemStack held = player.getItemInHand(hand);
        return result == net.minecraft.world.InteractionResult.CONSUME
                ? net.minecraft.world.InteractionResultHolder.consume(held)
                : net.minecraft.world.InteractionResultHolder.success(held);
    }
    *///?}

    //? if >=26.1 {
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
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
}
