package dev.golem.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.golem.core.world.BlockPlaces;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Fabric's block-use event precedes placement, and vanilla exposes no successful-fire callback.
// Widening cannot notify whether setBlockAndUpdate succeeded. The flint-and-steel twin is MixinFlintAndSteelPlaced.
@Mixin(FireChargeItem.class)
public abstract class MixinFireChargePlaced {

    @WrapOperation(method = "useOn", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean golem$firePlaced(Level level, BlockPos pos, BlockState state, Operation<Boolean> original,
                                     @Local(argsOnly = true) UseOnContext context) {
        boolean placed = original.call(level, pos, state);
        if (placed && state.getBlock() instanceof BaseFireBlock) {
            BlockPlaces.placed(context.getPlayer(), level, pos, state);
        }
        return placed;
    }
}
