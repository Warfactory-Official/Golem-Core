package dev.golem.core.world;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A digging tool (pickaxe, axe, shovel, or any mix of them) built from a {@link ToolTier}.
 *
 * <p>26.1 describes a tool entirely in item components on a plain {@code Item}; 1.21.1 and 1.20.1 use
 * {@code DiggerItem} with two different constructors and attribute models. Extending
 * {@code DiggerItem} there is deliberate: 1.20.1 decides which enchantments apply by
 * {@code instanceof DiggerItem}, and Fabric has no hook to answer that any other way.
 *
 * <p>Attack damage is the absolute main-hand modifier (the legacy {@code getItemAttributeModifiers}
 * value), not a baseline the material bonus is added to. Mining speed and "correct tool" answers come
 * from {@link Tools} on every target, so an override sees the same default everywhere.
 */
public class CompatToolItem extends
        //? if >=26.1 {
        Item
        //?} else {
        /*net.minecraft.world.item.DiggerItem
        *///?}
{
    protected final ToolTier toolTier;

    //? if forge {
    /*@Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        dev.golem.core.render.ForgeItemRenderers.initialize(consumer);
    }
    *///?}

    // SwingReceiver dispatch through the loader's own item hook (Fabric: MixinLivingEntitySwing).
    //? if neoforge {
    @Override
    public boolean onEntitySwing(net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.LivingEntity entity,
                                 net.minecraft.world.InteractionHand hand) {
        return SwingReceiver.dispatch(stack, entity, hand);
    }
    //?} elif forge {
    /*@Override
    public boolean onEntitySwing(net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return SwingReceiver.dispatch(stack, entity, SwingReceiver.handOf(stack, entity));
    }
    *///?}

    protected final List<TagKey<Block>> mineable;
    //? if <1.21.1 {
    /*private final float legacyDamage;
    private final float legacySpeed;
    private final double legacyMovement;
    *///?}

    public CompatToolItem(Item.Properties properties, ToolTier tier, List<TagKey<Block>> mineable,
                          float attackDamage, float attackSpeed, double movement) {
        //? if >=26.1 {
        super(modernProperties(properties, tier, mineable, attackDamage, attackSpeed, movement));
        //?} else if >=1.21.1 {
        /*super(Tools.vanillaTier(tier), mineable.get(0), Tools.unbreakableIfWorn(properties, tier)
                .attributes(Tools.attributes(attackDamage, attackSpeed, movement)));
        *///?} else {
        /*super(attackDamage - tier.attackDamageBonus(), attackSpeed, Tools.vanillaTier(tier), mineable.get(0),
                properties);
        this.legacyDamage = attackDamage;
        this.legacySpeed = attackSpeed;
        this.legacyMovement = movement;
        *///?}
        this.toolTier = tier;
        this.mineable = List.copyOf(mineable);
    }

    //? if >=26.1 {
    private static Item.Properties modernProperties(Item.Properties properties, ToolTier tier,
                                                    List<TagKey<Block>> mineable, float attackDamage,
                                                    float attackSpeed, double movement) {
        net.minecraft.core.HolderGetter<Block> blocks = net.minecraft.core.registries.BuiltInRegistries
                .acquireBootstrapRegistrationLookup(net.minecraft.core.registries.BuiltInRegistries.BLOCK);
        List<net.minecraft.world.item.component.Tool.Rule> rules = new java.util.ArrayList<>();
        rules.add(net.minecraft.world.item.component.Tool.Rule.deniesDrops(
                blocks.getOrThrow(Tools.incorrectFor(tier.level()))));
        for (TagKey<Block> tag : mineable) {
            rules.add(net.minecraft.world.item.component.Tool.Rule.minesAndDrops(blocks.getOrThrow(tag), tier.speed()));
        }
        properties.stacksTo(1)
                .component(net.minecraft.core.component.DataComponents.TOOL,
                        new net.minecraft.world.item.component.Tool(rules, 1.0F, 1, true))
                .component(net.minecraft.core.component.DataComponents.WEAPON,
                        new net.minecraft.world.item.component.Weapon(1));
        if (tier.enchantability() > 0) properties.enchantable(tier.enchantability());
        if (tier.uses() > 0) properties.durability(tier.uses());
        if (tier.repairItems() != null) properties.repairable(tier.repairItems());
        return properties.attributes(Tools.attributes(attackDamage, attackSpeed, movement));
    }
    //?}

    public ToolTier toolTier() {
        return toolTier;
    }

    /** Mining speed against {@code state}; defaults to the tier speed on the tool's mineable tags. */
    protected float compatDestroySpeed(ItemStack stack, BlockState state) {
        return Tools.destroySpeed(toolTier, mineable, state);
    }

    /**
     * Whether mining {@code state} with this tool yields drops. {@code stack} is empty on 1.20.1 Fabric,
     * whose vanilla hook carries no stack.
     */
    protected boolean compatIsCorrectToolForDrops(ItemStack stack, BlockState state) {
        return Tools.correctForDrops(toolTier, mineable, state);
    }

    /** Receives the successful melee-hit hook; the tool itself loses one durability afterwards. */
    protected void compatHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    }

    /** Adds this item's custom tooltip lines after vanilla's lines. */
    protected void appendCompatHoverText(ItemStack stack, CompatTooltipContext context,
                                         List<Component> tooltip, TooltipFlag flag) {
    }

    /** Right-click in the air; see {@link CompatItem#compatUse}. */
    protected InteractionResult compatUse(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    /** Server inventory tick; see {@link CompatItem#compatInventoryTick}. */
    protected void compatInventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level,
                                       Entity owner, EquipmentSlot slot, boolean held) {
    }

    /**
     * Whether the vanilla break of {@code pos} may go ahead. Returning false leaves the block to this
     * item, which is how a tool that breaks its own blocks (an area or harvest ability) takes over.
     */
    protected boolean compatCanDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                            LivingEntity user) {
        return true;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return compatDestroySpeed(stack, state);
    }

    //? if >=1.21.1 {
    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return compatIsCorrectToolForDrops(stack, state);
    }
    //?} else {
    /*@Override
    public boolean isCorrectToolForDrops(BlockState state) {
        return compatIsCorrectToolForDrops(ItemStack.EMPTY, state);
    }
    *///?}

    //? if forge && <1.21.1 {
    /*@Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return compatIsCorrectToolForDrops(stack, state);
    }
    *///?}

    //? if >=26.1 {
    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        compatHurtEnemy(stack, target, attacker);
    }
    //?} else if >=1.21.1 {
    /*@Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        compatHurtEnemy(stack, target, attacker);
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }
    *///?} else {
    /*@Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        compatHurtEnemy(stack, target, attacker);
        Tools.hurt(stack, 1, attacker);
        return true;
    }

    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            net.minecraft.world.entity.ai.attributes.AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND
                ? Tools.legacyAttributes(BASE_ATTACK_DAMAGE_UUID, BASE_ATTACK_SPEED_UUID, legacyDamage, legacySpeed,
                        legacyMovement)
                : super.getDefaultAttributeModifiers(slot);
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

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = compatUse(level, player, hand);
        return result == InteractionResult.PASS ? super.use(level, player, hand) : result;
    }

    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level, Entity owner,
                              EquipmentSlot slot) {
        compatInventoryTick(stack, level, owner, slot,
                slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND);
    }

    @Override
    public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
        return super.canDestroyBlock(stack, state, level, pos, user)
                && compatCanDestroyBlock(stack, state, level, pos, user);
    }
    //?} else {
    /*@Override
    public void appendHoverText(ItemStack stack,
                                //? if >=1.21.1 {
                                Item.TooltipContext context,
                                //?} else {
                                /^Level context,^/
                                //?}
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        appendCompatHoverText(stack, CompatTooltipContext.INSTANCE, tooltip, flag);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = compatUse(level, player, hand);
        ItemStack held = player.getItemInHand(hand);
        return result == InteractionResult.SUCCESS ? net.minecraft.world.InteractionResultHolder.success(held)
                : result == InteractionResult.CONSUME ? net.minecraft.world.InteractionResultHolder.consume(held)
                : super.use(level, player, hand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity owner, int slot, boolean selected) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            compatInventoryTick(stack, serverLevel, owner, null, selected);
        }
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return compatCanDestroyBlock(player.getMainHandItem(), state, level, pos, player);
    }
    *///?}
}
