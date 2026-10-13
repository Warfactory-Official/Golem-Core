package dev.golem.core.registry;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block whose block entity ticks, where the ticker is declared at the block entity's registration
 * rather than restated here.
 *
 * <p>Implement this instead of {@link EntityBlock} and write no {@code getTicker}. The side, type and method
 * are stated once by {@link BlockEntities#ticking}, beside the block entity. What is left is a lookup on the
 * type vanilla already resolved for the position, so a block serving several block-entity types needs no
 * branch and a block whose entity does not tick needs no null return.
 *
 * <p>This is an interface because the blocks that need it share no base class. A more specific interface
 * default wins over {@link EntityBlock}'s, unless a class in the chain declares {@code getTicker} itself,
 * because a class method beats any interface default.
 */
public interface TickingBlock extends EntityBlock {

    @Override
    default <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return BlockEntities.ticker(type, level.isClientSide());
    }
}
