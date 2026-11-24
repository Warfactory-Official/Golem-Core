package dev.golem.core.world;

import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/** A deferred recipe result: a template on 26.1 and a copied stack on older targets. */
public final class ItemStackSpec {

    //? if >=1.21.11 {
    private final net.minecraft.world.item.ItemStackTemplate template;
    //?} else {
    /*private final ItemStack stack;
    *///?}

    //? if >=1.21.11 {
    private ItemStackSpec(net.minecraft.world.item.ItemStackTemplate template) {
        this.template = Objects.requireNonNull(template);
    }
    //?} else {
    /*private ItemStackSpec(ItemStack stack) {
        this.stack = Objects.requireNonNull(stack);
    }
    *///?}

    /** Captures a concrete stack without sharing mutable legacy data. */
    public static ItemStackSpec of(ItemStack stack) {
        Objects.requireNonNull(stack);
        //? if >=1.21.11 {
        return new ItemStackSpec(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(stack));
        //?} else {
        /*return new ItemStackSpec(stack.copy());
        *///?}
    }

    //? if >=1.21.11 {
    /** Stores a decoded template without creating its item stack. */
    public static ItemStackSpec fromTemplate(net.minecraft.world.item.ItemStackTemplate template) {
        return new ItemStackSpec(template);
    }

    /** The deferred 26.1 representation, suitable for recipe displays and codecs. */
    public net.minecraft.world.item.ItemStackTemplate template() {
        return template;
    }
    //?}

    //? if >=1.21.11 {
    /** A recipe-display slot showing {@code template}. */
    public static net.minecraft.world.item.crafting.display.SlotDisplay slot(
            net.minecraft.world.item.ItemStackTemplate template) {
        //? if >=26.1 {
        return new net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay(template);
        //?} else {
        /*return new net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay(template.create());
        *///?}
    }
    //?}

    /** Creates the configured result at the point vanilla asks to assemble it. */
    public ItemStack create() {
        //? if >=1.21.11 {
        return template.create();
        //?} else {
        /*return stack.copy();
        *///?}
    }
}
