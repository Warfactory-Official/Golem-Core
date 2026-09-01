package dev.golem.core.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Block-entity type construction across loader and constructor differences. */
public final class BlockEntities {

    private BlockEntities() {}

    @FunctionalInterface
    public interface Factory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }

    public static <T extends BlockEntity> BlockEntityType<T> type(Factory<T> factory, Block... blocks) {
        //? if fabric {
        /*return net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
                .<T>create(factory::create, blocks).build();
        *///?} elif neoforge && >=26.1 {
        return new BlockEntityType<>(factory::create, java.util.Set.of(blocks));
        //?} elif neoforge {
        /*return new BlockEntityType<T>(factory::create, java.util.Set.of(blocks), null);
        *///?} elif forge {
        /*return BlockEntityType.Builder.of(factory::create, blocks).build(null);
        *///?}
    }
}
