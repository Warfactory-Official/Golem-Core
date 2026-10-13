package dev.golem.core.world;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Base fire block with a version-neutral entity-inside hook. */
public abstract class CompatFireBlock extends BaseFireBlock {

    //? if >=1.21.1 {
    private final com.mojang.serialization.MapCodec<? extends BaseFireBlock> blockCodec;
    //?}

    /**
     * @param factory this block's own constructor reference, used to build the block codec.
     *
     * <p>From 1.21.1 every block carries a {@code MapCodec} and overrides {@code codec()}; before that
     * neither the type nor the method exists, so a subclass declaring one needs three separate guards
     * (the import, the field, the override). Taking the factory here moves all three into this class:
     * the codec is built with {@code simpleCodec}, which is {@code protected static} on
     * {@code BlockBehaviour} and therefore only reachable from a subclass like this one.
     *
     * <p>Safe for leaf classes only: the codec is built from the factory it is handed, so a subclass
     * that passes its parent's constructor would round-trip to the wrong type.
     */
    protected <B extends CompatFireBlock> CompatFireBlock(BlockBehaviour.Properties properties, float fireDamage,
                                                          Function<BlockBehaviour.Properties, B> factory) {
        super(properties, fireDamage);
        //? if >=1.21.1 {
        this.blockCodec = simpleCodec(factory);
        //?}
    }

    //? if >=1.21.1 {
    @Override
    public com.mojang.serialization.MapCodec<? extends BaseFireBlock> codec() {
        return blockCodec;
    }
    //?}

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
