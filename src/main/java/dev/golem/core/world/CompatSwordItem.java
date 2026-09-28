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

/**
 * A vanilla sword item with one constructor across the supported tool-material APIs.
 *
 * <p>The modern 26.1 API stores sword attributes on {@code Item.Properties}; 1.21.1 still uses
 * {@code SwordItem} but moved its constructor and Tier harvest method; 1.20.1 retains the older
 * {@code SwordItem(Tier, int, float, Properties)} spelling. The seam keeps those API details in Core.
 *
 * <p>Two constructors: the original steel one (damage is a baseline the steel bonus is added to) and
 * the {@link ToolTier} one, whose {@code attackDamage} is the absolute main-hand modifier the way
 * legacy {@code getItemAttributeModifiers} overrides wrote it, optionally with a movement modifier.
 */
public class CompatSwordItem extends
        //? if >=26.1 {
        net.minecraft.world.item.Item
        //?} else {
        /*net.minecraft.world.item.SwordItem
        *///?}
{
    private final boolean wearsOnHit;

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

    //? if <1.21.1 {
    /*private final boolean absoluteAttributes;
    private final float legacyDamage;
    private final float legacySpeed;
    private final double legacyMovement;
    *///?}

    public CompatSwordItem(net.minecraft.world.item.Item.Properties properties,
                           int durability, float attackDamage, float attackSpeed,
                           net.minecraft.tags.TagKey<net.minecraft.world.item.Item> repairTag) {
        //? if >=26.1 {
        super(properties.sword(steelMaterial(durability, repairTag), attackDamage, attackSpeed)
                .durability(durability)
                .repairable(repairTag));
        //?} else if >=1.21.1 {
        /*super(steelTier(durability, repairTag),
                properties.durability(durability).attributes(
                        net.minecraft.world.item.SwordItem.createAttributes(
                                steelTier(durability, repairTag), (int) attackDamage, attackSpeed)));
        *///?} else {
        /*super(steelTier(durability, repairTag), (int) attackDamage, attackSpeed,
                properties.durability(durability));
        this.absoluteAttributes = false;
        this.legacyDamage = 0F;
        this.legacySpeed = 0F;
        this.legacyMovement = 0D;
        *///?}
        this.wearsOnHit = true;
    }

    /** A sword of {@code tier} whose main-hand attack modifier is exactly {@code attackDamage}. */
    public CompatSwordItem(net.minecraft.world.item.Item.Properties properties, ToolTier tier,
                           float attackDamage, float attackSpeed, double movement) {
        this(properties, tier, attackDamage, attackSpeed, movement, true);
    }

    /**
     * The same sword, choosing whether a successful hit costs it one durability. Legacy weapons that
     * overrode {@code hitEntity} without calling the sword's own never wore on a hit, only on digging.
     */
    public CompatSwordItem(net.minecraft.world.item.Item.Properties properties, ToolTier tier,
                           float attackDamage, float attackSpeed, double movement, boolean wearsOnHit) {
        //? if >=26.1 {
        super(modernSword(properties, tier, attackDamage, attackSpeed, movement, wearsOnHit));
        //?} else if >=1.21.1 {
        /*super(Tools.vanillaTier(tier), Tools.unbreakableIfWorn(properties, tier)
                .attributes(Tools.attributes(attackDamage, attackSpeed, movement)));
        *///?} else {
        /*super(Tools.vanillaTier(tier), 0, attackSpeed, properties);
        this.absoluteAttributes = true;
        this.legacyDamage = attackDamage;
        this.legacySpeed = attackSpeed;
        this.legacyMovement = movement;
        *///?}
        this.wearsOnHit = wearsOnHit;
    }

    //? if >=26.1 {
    private static Item.Properties modernSword(Item.Properties properties, ToolTier tier, float attackDamage,
                                               float attackSpeed, double movement, boolean wearsOnHit) {
        net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blocks =
                net.minecraft.core.registries.BuiltInRegistries.acquireBootstrapRegistrationLookup(
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK);
        properties.stacksTo(1)
                .component(net.minecraft.core.component.DataComponents.TOOL,
                        new net.minecraft.world.item.component.Tool(List.of(
                                net.minecraft.world.item.component.Tool.Rule.minesAndDrops(
                                        net.minecraft.core.HolderSet.direct(
                                                net.minecraft.world.level.block.Blocks.COBWEB.builtInRegistryHolder()), 15.0F),
                                net.minecraft.world.item.component.Tool.Rule.overrideSpeed(
                                        blocks.getOrThrow(net.minecraft.tags.BlockTags.SWORD_INSTANTLY_MINES), Float.MAX_VALUE),
                                net.minecraft.world.item.component.Tool.Rule.overrideSpeed(
                                        blocks.getOrThrow(net.minecraft.tags.BlockTags.SWORD_EFFICIENT), 1.5F)),
                                1.0F, 2, false))
                .component(net.minecraft.core.component.DataComponents.WEAPON,
                        new net.minecraft.world.item.component.Weapon(wearsOnHit ? 1 : 0));
        if (tier.enchantability() > 0) properties.enchantable(tier.enchantability());
        if (tier.uses() > 0) properties.durability(tier.uses());
        if (tier.repairItems() != null) properties.repairable(tier.repairItems());
        return properties.attributes(Tools.attributes(attackDamage, attackSpeed, movement));
    }

    /** The original HBM steel tool material: level 3, 750 uses, speed 8, damage bonus 2, ench 10. */
    private static net.minecraft.world.item.ToolMaterial steelMaterial(
            int durability, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> repairTag) {
        return new net.minecraft.world.item.ToolMaterial(
                net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
                durability, 8.0F, 2.0F, 10, repairTag);
    }
    //?} else {
    /*private static net.minecraft.world.item.Tier steelTier(
            int durability, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> repairTag) {
        return new net.minecraft.world.item.Tier() {
            @Override public int getUses() { return durability; }
            @Override public float getSpeed() { return 8.0F; }
            @Override public float getAttackDamageBonus() { return 2.0F; }
            //? if >=1.21.1 {
            @Override public net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>
                    getIncorrectBlocksForDrops() {
                return net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
            }
            //?} else {
            /^@Override public int getLevel() { return 3; }^/
            //?}
            @Override public int getEnchantmentValue() { return 10; }
            @Override public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
                return net.minecraft.world.item.crafting.Ingredient.of(repairTag);
            }
        };
    }
    *///?}

    /** Receives the successful melee-hit hook; the sword loses one durability as vanilla does. */
    protected void compatHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    }

    /** Adds this item's custom tooltip lines after vanilla's lines. */
    protected void appendCompatHoverText(ItemStack stack, CompatTooltipContext context,
                                         List<Component> tooltip, TooltipFlag flag) {
    }

    /** Right-click; see {@link CompatItem#compatUse}. PASS falls back to vanilla. */
    protected InteractionResult compatUse(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    /** Server inventory tick; see {@link CompatItem#compatInventoryTick}. */
    protected void compatInventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level,
                                       Entity owner, EquipmentSlot slot, boolean held) {
    }

    //? if >=26.1 {
    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        compatHurtEnemy(stack, target, attacker);
    }

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
    //?} else {
    /*@Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        compatHurtEnemy(stack, target, attacker);
        //? if >=1.21.1 {
        return super.hurtEnemy(stack, target, attacker);
        //?} else {
        /^return wearsOnHit ? super.hurtEnemy(stack, target, attacker) : true;
        ^///?}
    }

    //? if >=1.21.1 {
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (wearsOnHit) super.postHurtEnemy(stack, target, attacker);
    }
    //?}

    @Override
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
    *///?}

    //? if <1.21.1 {
    /*@Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            net.minecraft.world.entity.ai.attributes.AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (absoluteAttributes && slot == EquipmentSlot.MAINHAND) {
            return Tools.legacyAttributes(BASE_ATTACK_DAMAGE_UUID, BASE_ATTACK_SPEED_UUID, legacyDamage, legacySpeed,
                    legacyMovement);
        }
        return super.getDefaultAttributeModifiers(slot);
    }
    *///?}
}
