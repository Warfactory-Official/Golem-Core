package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Base fire block with a version-neutral entity-inside hook. */
public abstract class CompatFireBlock extends BaseFireBlock {

    protected CompatFireBlock(BlockBehaviour.Properties properties, float fireDamage) {
        super(properties, fireDamage);
    }

    protected void onEntityInside(BlockState state, Level level, BlockPos pos, Entity entity) {}

    //? if >=26.1 {
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                             net.minecraft.world.entity.InsideBlockEffectApplier effects, boolean precise) {
        onEntityInside(state, level, pos, entity);
    }
    //?} else {
    /*@Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        onEntityInside(state, level, pos, entity);
    }
    *///?}
}
