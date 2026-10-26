package dev.golem.core.mixin;

import dev.golem.core.world.BlockChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla has no committed-state callback; loader placement events miss machine and property writes.
 * Widening cannot add notification, so publish after native state and block-entity callbacks.
 */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunkBlockChanges {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void golem$blockChanged(BlockPos pos, BlockState state,
            //? if >=26.1 {
            int flags,
            //?} else {
            /*boolean movedByPiston,
            *///?}
            CallbackInfoReturnable<BlockState> callback) {
        var before = callback.getReturnValue();
        if (before == null || BlockChanges.HANDLERS.isEmpty()) return;
        var chunk = (LevelChunk) (Object) this;
        if (chunk.getLevel() instanceof ServerLevel level) {
            BlockChanges.committed(level, pos, before, chunk.getBlockState(pos));
        }
    }
}
