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

    private final ArmorSlot armorSlot;

    @SuppressWarnings("unchecked")
    public CompatArmorItem(GolemArmorMaterial material, ArmorSlot slot, Item.Properties properties) {
        //? if >=26.1 {
        super(configure((net.minecraft.world.item.equipment.ArmorMaterial) material.nativeMaterial(), slot,
                properties, material.enchantmentValue()));
        //?} else if >=1.21.1 {
        /*super((net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial>) material.nativeMaterial(), type(slot), properties);
        *///?} else {
        /*super((net.minecraft.world.item.ArmorMaterial) material.nativeMaterial(), type(slot), properties);
        *///?}
        this.armorSlot = slot;
    }

    /** The equipment slot represented by this item, including on versions without an item-side accessor. */
    public final ArmorSlot armorSlot() {
        return armorSlot;
    }

    //? if forge {
    /*/^*
     * Forge 1.20.1 asks the ITEM for its custom renderer, so every item base Golem offers has to carry
     * this or the item silently loses its icon.
     *
     * <p>This is the one loader where the hook is a virtual method rather than a registration: Fabric
     * registers a {@code DynamicItemRenderer} per item and NeoForge 1.21.1 registers a client extension
     * per item, both walking the item registry from outside, so there the item's CLASS is irrelevant.
     * Here it is everything — an item that does not inherit this override is never offered to the
     * renderer, its {@code builtin/entity} model has no geometry of its own, and it draws nothing.
     * Keep this in lockstep with {@link CompatItem} and {@link CompatBlockItem}.
     ^/
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        dev.golem.core.render.ForgeItemRenderers.initialize(consumer);
    }
    *///?}

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
    /**
     * Applies the material, working around one hard edge in {@code Properties.humanoidArmor}: it calls
     * {@code enchantable(material.enchantmentValue())} unconditionally, and that method throws on a value
     * of 0 — {@code IllegalArgumentException: Enchantment value must be positive}. Power armour declares
     * exactly that, meaning "never enchantable", so for those materials the rest of {@code humanoidArmor}
     * is applied by hand with the enchantable component simply left off.
     *
     * <p>The failure this avoids is not subtle at runtime but is entirely invisible at compile time: six
     * nodes built cleanly and the dedicated server died during registry init.
     */
    private static Item.Properties configure(net.minecraft.world.item.equipment.ArmorMaterial material,
                                             ArmorSlot slot, Item.Properties properties,
                                             int enchantmentValue) {
        net.minecraft.world.item.equipment.ArmorType type = switch (slot) {
            case HELMET -> net.minecraft.world.item.equipment.ArmorType.HELMET;
            case CHESTPLATE -> net.minecraft.world.item.equipment.ArmorType.CHESTPLATE;
            case LEGGINGS -> net.minecraft.world.item.equipment.ArmorType.LEGGINGS;
            case BOOTS -> net.minecraft.world.item.equipment.ArmorType.BOOTS;
        };

        if (enchantmentValue > 0) return properties.humanoidArmor(material, type);

        return properties
                .durability(type.getDurability(material.durability()))
                .attributes(material.createAttributes(type))
                .component(net.minecraft.core.component.DataComponents.EQUIPPABLE,
                        net.minecraft.world.item.equipment.Equippable.builder(type.getSlot())
                                .setEquipSound(material.equipSound())
                                .setAsset(material.assetId())
                                .build())
                .repairable(material.repairIngredient());
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
