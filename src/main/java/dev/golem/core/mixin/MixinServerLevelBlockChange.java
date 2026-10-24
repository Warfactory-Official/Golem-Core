package dev.golem.core.mixin;

import dev.golem.core.world.BlockChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link BlockChanges}. Why a mixin (AGENTS.md section 7): (1) vanilla's {@code onPlace}/{@code onRemove} reach only the
 * changed block's own class, never a block a tag names; (2) no loader has an event for every change (place and
 * neighbour-notify events miss pistons, explosions and flag-less sets; Fabric has none); (3) visibility is not the
 * obstacle. One HEAD {@code @Inject} on the POI update vanilla runs once per applied change with both states.
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevelBlockChange {

    //? if >=26.1 {
    @Inject(method = "updatePOIOnBlockStateChange", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "onBlockStateChange", at = @At("HEAD"))
    *///?}
    private void golem$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        BlockChanges.changed((ServerLevel) (Object) this, pos, oldState, newState);
    }
}
