package dev.golem.core.world;

import dev.golem.core.data.CompatBlockEntity;
import net.minecraft.core.BlockPos;
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
        BlockPos pos = BlockPos.containing(params.getParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN));
        ItemStack stack = new ItemStack(asItem());
        if (params.getLevel().getBlockEntity(pos) instanceof CompatBlockEntity entity) {
            entity.saveToItemCompat(stack);
        }
        return java.util.List.of(stack);
    }
    *///?}
}
