package dev.golem.core.world;

import dev.golem.core.data.CompatBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Entity block whose persisted block-entity state travels with its dropped item on every era. */
public abstract class CarriedBlock extends CompatBlock implements EntityBlock {

    protected CarriedBlock(Properties properties) {
        super(properties);
    }

    //? if <1.20.5 {
    /*@Override
    public java.util.List<ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        ItemStack stack = new ItemStack(asItem());
        // a player break removes the block before its drops, so the level no longer has the block entity
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof CompatBlockEntity entity) {
            entity.saveToItemCompat(stack);
        }
        return java.util.List.of(stack);
    }
    *///?}
}
