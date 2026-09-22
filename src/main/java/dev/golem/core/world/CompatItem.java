package dev.golem.core.world;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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

    /**
     * Whether the item already raised in hand is still this one, its components having changed.
     *
     * <p>The hand renderer lowers and raises the item whenever the held stack stops matching the one it
     * is showing, components and all. An item that rewrites its own state every tick — a weapon
     * counting an animation down, a tool running a timer — therefore never finishes being raised and
     * stays where a freshly drawn item starts, some six tenths of a block below where it belongs. Only
     * a renderer that keeps the pose vanilla hands it can see that, which is why it hides so well.
     *
     * <p>1.7 compared the item and its damage and nothing else, so this is the question it asked.
     * Answering {@code true} keeps the raise the item has already made; a genuine change of item is a
     * different question and still lowers the hand, so an item normally answers by comparing the two
     * items alone.
     */
    public boolean compatIsSameHeldItem(ItemStack shown, ItemStack held) {
        return false;
    }

    /** Adds this item's custom tooltip lines after vanilla's lines. */
    protected void appendCompatHoverText(ItemStack stack, CompatTooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
    }

    /** Handles use with a stable return type across the supported vanilla APIs. */
    protected InteractionResult compatUse(Level level, Player player, InteractionHand hand) {
        return compatVanillaUse(level, player, hand);
    }

    /** Calls the vanilla item implementation without exposing its return-type split to callers. */
    protected final InteractionResult compatVanillaUse(Level level, Player player, InteractionHand hand) {
        //? if >=26.1 {
        return super.use(level, player, hand);
        //?} else {
        /*net.minecraft.world.InteractionResultHolder<ItemStack> result = super.use(level, player, hand);
        return result.getResult();
        *///?}
    }

    /** Receives the successful melee-hit hook used by syringe-like items. */
    protected boolean compatHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return false;
    }

    /** Item use duration in ticks. Vanilla's default is 32; subclasses override when needed. */
    protected int compatUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    /** Stable use animation names for items whose held-use callback spans the supported API eras. */
    protected UseAnimation compatUseAnimation(ItemStack stack) {
        return UseAnimation.NONE;
    }

    /** Stable release callback; older targets discard the boolean result because their hook is void. */
    protected boolean compatReleaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        return false;
    }

    public enum UseAnimation { NONE, BOW, DRINK }

    /**
     * Receives server-side inventory ticks with the optional equipped slot when vanilla provides it.
     *
     * <p>{@code held} is supplied separately because the two eras express it differently and neither
     * spelling survives the other: 26.1 reports the {@link EquipmentSlot} an item occupies (null for a
     * plain inventory slot), while older versions report a slot index plus a {@code selected} flag that
     * is only ever true for the main hand. Held-item logic needs the answer, not the spelling.
     *
     * <p>One consequence worth knowing: on 26.1 an offhand item counts as held, below it does not,
     * because the old callback has no way to say so.
     */
    protected void compatInventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level,
                                       Entity owner, EquipmentSlot slot, boolean held) {
    }

    /** Runs this item's server inventory tick from outside (an AI holding the item), in the 26.1 shape. */
    public final void tickInventoryCompat(ItemStack stack, net.minecraft.server.level.ServerLevel level,
                                          Entity owner, EquipmentSlot slot) {
        compatInventoryTick(stack, level, owner, slot,
                slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND);
    }

    /**
     * Whether this item may break a block at all. Returning false leaves the block untouched, which is
     * what an item bound to the attack button — a gun — needs.
     *
     * <p>26.1 renamed the hook to {@code canDestroyBlock} and widened the user to {@code LivingEntity};
     * before that it was {@code canAttackBlock} taking a {@code Player}.
     */
    protected boolean compatCanDestroyBlock(ItemStack stack, net.minecraft.world.level.block.state.BlockState state,
                                             Level level, net.minecraft.core.BlockPos pos,
                                             net.minecraft.world.entity.LivingEntity user) {
        return true;
    }

    //? if >=26.1 {
    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        compatHurtEnemy(stack, target, attacker);
    }
    //?} else {
    /*@Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return compatHurtEnemy(stack, target, attacker);
    }
    *///?}

    //? if >=1.20.5 {
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return compatUseDuration(stack, entity);
    }

    //?} else {
    /*@Override
    public int getUseDuration(ItemStack stack) {
        return compatUseDuration(stack, null);
    }
    *///?}

    //? if >=26.1 {
    @Override
    public net.minecraft.world.item.ItemUseAnimation getUseAnimation(ItemStack stack) {
        return switch (compatUseAnimation(stack)) {
            case NONE -> net.minecraft.world.item.ItemUseAnimation.NONE;
            case BOW -> net.minecraft.world.item.ItemUseAnimation.BOW;
            case DRINK -> net.minecraft.world.item.ItemUseAnimation.DRINK;
        };
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        return compatReleaseUsing(stack, level, entity, timeLeft);
    }
    //?} else {
    /*@Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return switch (compatUseAnimation(stack)) {
            case NONE -> net.minecraft.world.item.UseAnim.NONE;
            case BOW -> net.minecraft.world.item.UseAnim.BOW;
            case DRINK -> net.minecraft.world.item.UseAnim.DRINK;
        };
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        compatReleaseUsing(stack, level, entity, timeLeft);
    }
    *///?}

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
        compatInventoryTick(stack, level, owner, slot,
                slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND);
    }

    @Override
    public boolean canDestroyBlock(ItemStack stack, net.minecraft.world.level.block.state.BlockState state,
                                   Level level, net.minecraft.core.BlockPos pos,
                                   net.minecraft.world.entity.LivingEntity user) {
        return compatCanDestroyBlock(stack, state, level, pos, user);
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
            compatInventoryTick(stack, serverLevel, owner, null, selected);
        }
    }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level,
                                  net.minecraft.core.BlockPos pos, Player player) {
        return compatCanDestroyBlock(player.getMainHandItem(), state, level, pos, player);
    }
    *///?}
}
