package dev.golem.core.world;

import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.DropExperienceBlock;

/** A drop-experience block whose constructor argument order changed after 1.20.1. */
public class CompatDropExperienceBlock extends DropExperienceBlock {

    public CompatDropExperienceBlock(IntProvider experience, Properties properties) {
        //? if >=1.20.5 {
        super(experience, properties);
        //?} else {
        /*super(properties, experience);
        *///?}
    }
}
