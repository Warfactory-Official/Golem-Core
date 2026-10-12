package dev.golem.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fabric's {@link dev.golem.core.world.BlockPlaces#onPlace} hook: reports a block a {@code BlockItem} placed, right after
 * vanilla's {@code setPlacedBy} - where Forge and NeoForge fire {@code BlockEvent.EntityPlaceEvent}.
 *
 * <p>Why a mixin (§7). Rung 1, vanilla: {@code Block.setPlacedBy} belongs to each block, so a mod cannot see another
 * mod's (or vanilla's) blocks being placed. Rung 2, loaders: Forge-like loaders use their place event, so this class is
 * listed only in {@code golemcore.fabric.mixins.json}; Fabric API has no block-place event ({@code UseBlockCallback}
 * runs before the use and cannot tell whether a block was placed). Rung 3, wideners: visibility is not the problem. The
 * smallest mixin is one {@code @WrapOperation} around the single {@code setPlacedBy} call in {@code BlockItem.place},
 * which keeps vanilla's placement untouched; its descriptor is the same on 1.20.1, 1.21.1 and 26.1.
 */
@Mixin(BlockItem.class)
public abstract class MixinBlockItemPlaced {

    @WrapOperation(method = "place", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void golem$placed(Block block, Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack,
                              Operation<Void> original) {
        original.call(block, level, pos, state, placer, stack);
        dev.golem.core.world.BlockPlaces.placed(placer, level, pos, level.getBlockState(pos));
    }
}
