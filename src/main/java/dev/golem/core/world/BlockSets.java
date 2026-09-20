package dev.golem.core.world;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * Vanilla's door and trapdoor blocks, and the {@link BlockSetType} they take, spelled once.
 *
 * <p>1.20.1 orders the door constructors {@code (Properties, BlockSetType)} and its
 * {@link BlockSetType} record has eleven components; 1.21.1 and 26.1 order them
 * {@code (BlockSetType, Properties)} and add the wind-charge, arrow-button and pressure-plate
 * sensitivity components. A mod that only wants "a metal door that plays this sound" states that here
 * and gets the same block on every node.
 */
public final class BlockSets {

    private BlockSets() {}

    /**
     * A hand-openable block set with the given sound type and door/trapdoor sounds. The pressure-plate
     * and button sounds are vanilla's metal ones, since no HBM plate or button is built on it.
     */
    public static BlockSetType type(String name, SoundType soundType,
                                    SoundEvent doorClose, SoundEvent doorOpen,
                                    SoundEvent trapdoorClose, SoundEvent trapdoorOpen) {
        // Registered so the block codecs' by-name lookup can find it, as vanilla does for its own sets.
        //? if >=1.21.1 {
        return BlockSetType.register(new BlockSetType(name, true, true, true,
                BlockSetType.PressurePlateSensitivity.EVERYTHING,
                soundType, doorClose, doorOpen, trapdoorClose, trapdoorOpen,
                SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
                SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON));
        //?} else {
        /*return BlockSetType.register(new BlockSetType(name, true, soundType, doorClose, doorOpen, trapdoorClose, trapdoorOpen,
                SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
                SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON));
        *///?}
    }

    /** A plain vanilla two-block door of the given set. */
    public static DoorBlock door(BlockSetType type, BlockBehaviour.Properties properties) {
        //? if >=1.21.1 {
        return new DoorBlock(type, properties);
        //?} else {
        /*return new DoorBlock(properties, type);
        *///?}
    }

    /** A plain vanilla trapdoor of the given set. */
    public static TrapDoorBlock trapdoor(BlockSetType type, BlockBehaviour.Properties properties) {
        //? if >=1.21.1 {
        return new TrapDoorBlock(type, properties);
        //?} else {
        /*return new TrapDoorBlock(properties, type);
        *///?}
    }
}
