package dev.golem.core.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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

    /** Saves a block entity with its type and position without exposing the registry-aware signature split. */
    public static CompoundTag save(BlockEntity blockEntity, HolderLookup.Provider registries) {
        //? if >=1.20.5 {
        return blockEntity.saveWithFullMetadata(registries);
        //?} else {
        /*return blockEntity.saveWithFullMetadata();
        *///?}
    }

    /** Recreates a registered block entity from custom data across the loadStatic signature split. */
    public static BlockEntity load(BlockPos pos, BlockState state, CompoundTag tag,
                                   HolderLookup.Provider registries) {
        //? if >=1.20.5 {
        return BlockEntity.loadStatic(pos, state, tag, registries);
        //?} else {
        /*return BlockEntity.loadStatic(pos, state, tag);
        *///?}
    }
}
