package dev.golem.core.mixin;

import dev.golem.core.world.ChunkPromotions;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The only place a rising edge into full accessibility is observable.
 *
 * <p>Neither loader raises an event for it: both fire on the chunk entering the level, which is a
 * different moment and a strictly rarer one. Nothing can be access-widened into existence here
 * either, because the notification is not published; this is the smallest bridge available.
 */
@Mixin(ChunkMap.class)
public abstract class MixinChunkMapPromotion {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "onFullChunkStatusChange", at = @At("TAIL"))
    private void golem$chunkStatusChanged(ChunkPos pos, FullChunkStatus status, CallbackInfo ci) {
        ChunkPromotions.statusChanged(level, pos, status);
    }
}
