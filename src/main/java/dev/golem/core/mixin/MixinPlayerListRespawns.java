package dev.golem.core.mixin;

import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.golem.core.world.Respawns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla has no pre-lookup respawn destination event; loader notifications follow the consumed spawn block.
 * Access widening cannot replace the lookup's point and destination while preserving the saved point.
 */
@Mixin(PlayerList.class)
public abstract class MixinPlayerListRespawns {
    //? if <1.21 {
    /*@Inject(method = "respawn", at = @At("HEAD"))
    private void golem$chooseRespawn(ServerPlayer player, boolean keepEverything, CallbackInfoReturnable<ServerPlayer> result,
            @Share("golem$destination") LocalRef<Respawns.Destination> destination) {
        destination.set(Respawns.RULES.invoker().destination(player));
    }

    @ModifyExpressionValue(method = "respawn", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/server/level/ServerPlayer;getRespawnPosition()Lnet/minecraft/core/BlockPos;"))
    private @Nullable BlockPos golem$lookupPoint(@Nullable BlockPos point,
            @Share("golem$destination") LocalRef<Respawns.Destination> destination) {
        return destination.get() == null ? point : null;
    }

    @ModifyExpressionValue(method = "respawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;overworld()Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel golem$destination(ServerLevel vanilla,
            @Local(argsOnly = true) ServerPlayer player,
            @Share("golem$destination") LocalRef<Respawns.Destination> destination) {
        var choice = destination.get();
        if (choice == null) choice = Respawns.FALLBACKS.invoker().destination(player);
        return choice instanceof Respawns.WorldSpawn world ? world.level() : vanilla;
    }

    @Inject(method = "respawn", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/server/level/ServerPlayer;restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V"))
    private void golem$preservePoint(ServerPlayer player, boolean keepEverything, CallbackInfoReturnable<ServerPlayer> result,
            @Local(ordinal = 1) ServerPlayer replacement,
            @Share("golem$destination") LocalRef<Respawns.Destination> destination) {
        if (destination.get() != null) {
            replacement.setRespawnPosition(player.getRespawnDimension(), player.getRespawnPosition(),
                    player.getRespawnAngle(), player.isRespawnForced(), false);
            replacement.setYRot(replacement.serverLevel().getSharedSpawnAngle());
            replacement.setXRot(0F);
        }
    }
    *///?}
}
