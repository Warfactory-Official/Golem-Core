package dev.golem.core.world;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * Vanilla's two-block door as a base class, with one constructor on every node: 1.20.1 orders
 * {@link DoorBlock}'s constructor {@code (Properties, BlockSetType)}, 1.21.1 and 26.1 {@code (BlockSetType, Properties)}.
 * {@link BlockSets#door} builds a plain door; a mod that changes a door's shape or behaviour subclasses this instead.
 */
public class CompatDoorBlock extends DoorBlock {

    public CompatDoorBlock(BlockSetType type, Properties properties) {
        //? if >=1.21.1 {
        super(type, properties);
        //?} else {
        /*super(properties, type);
        *///?}
    }
}
