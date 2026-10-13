package dev.golem.core.world;

import java.util.function.Function;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Falling block that owns the version-specific codec contract.
 *
 * <p>Same reasoning as {@link CompatFireBlock}: from 1.21.1 a block declares a {@code MapCodec} and
 * overrides {@code codec()}, and before that neither exists, so a subclass doing it itself needs a
 * guard on the import, the field and the override. Handing the constructor reference here moves all
 * three into this class.
 *
 * <p>Safe for leaf classes only; see {@link CompatFireBlock} for why.
 */
public abstract class CompatFallingBlock extends FallingBlock {

    //? if >=1.21.1 {
    private final com.mojang.serialization.MapCodec<? extends FallingBlock> blockCodec;
    //?}

    /** @param factory this block's own constructor reference, used to build the block codec. */
    protected <B extends CompatFallingBlock> CompatFallingBlock(BlockBehaviour.Properties properties,
                                                                Function<BlockBehaviour.Properties, B> factory) {
        super(properties);
        //? if >=1.21.1 {
        this.blockCodec = simpleCodec(factory);
        //?}
    }

    //? if >=1.21.1 {
    @Override
    public com.mojang.serialization.MapCodec<? extends FallingBlock> codec() {
        return blockCodec;
    }
    //?}
}
