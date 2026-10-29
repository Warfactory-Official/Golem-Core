package dev.golem.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.golem.core.world.Respawns;
import dev.golem.core.world.WorldAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
//? if >=26.1 {
import net.minecraft.world.level.portal.TeleportTransition;
//?} elif >=1.21 {
/*import net.minecraft.world.level.portal.DimensionTransition;
 *///?}

/**
 * Vanilla has no respawn selector; loader respawn events run after spawn-block lookup.
 * Loaders supply sleep-time results, and Fabric supplies spawn-setting events.
 * Widening cannot replace the remaining lookup and bed-world gates.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinRespawnSelection {
    private static final String BED_LOOKUP = "*(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;";

    //? if >=26.1 {
    @Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true)
    private void golem$respawnTransition(boolean consumeSpawnBlock, TeleportTransition.PostTeleportTransition after,
                                         CallbackInfoReturnable<TeleportTransition> result) {
        var player = (ServerPlayer) (Object) this;
        var destination = Respawns.RULES.invoker().destination(player);
        if (destination == null) return;
        if (destination == Respawns.DEFAULT) {
            result.setReturnValue(TeleportTransition.createDefault(player, after));
        } else {
            var level = ((Respawns.WorldSpawn) destination).level();
            var spawn = level.getRespawnData();
            result.setReturnValue(new TeleportTransition(level,
                    Vec3.atBottomCenterOf(player.adjustSpawnLocation(level, spawn.pos())), Vec3.ZERO,
                    spawn.yaw(), spawn.pitch(), after));
        }
    }

    @WrapOperation(method = "findRespawnPositionAndUseSpawnBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/portal/TeleportTransition;missingRespawnBlock(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;)Lnet/minecraft/world/level/portal/TeleportTransition;"))
    private TeleportTransition golem$missingSpawn(ServerPlayer player, TeleportTransition.PostTeleportTransition after,
                                                  Operation<TeleportTransition> original) {
        var destination = Respawns.FALLBACKS.invoker().destination(player);
        if (!(destination instanceof Respawns.WorldSpawn world)) return original.call(player, after);
        var level = world.level();
        var spawn = level.getRespawnData();
        return new TeleportTransition(level, Vec3.atBottomCenterOf(player.adjustSpawnLocation(level, spawn.pos())),
                Vec3.ZERO, spawn.yaw(), spawn.pitch(), true, false, Set.of(), after);
    }
    //?} elif >=1.21 {
    /*@Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true)
    private void golem$respawnTransition(boolean keepInventory, DimensionTransition.PostDimensionTransition after,
            CallbackInfoReturnable<DimensionTransition> result) {
        var player = (ServerPlayer) (Object) this;
        var destination = Respawns.RULES.invoker().destination(player);
        if (destination == null) return;
        var level = destination instanceof Respawns.WorldSpawn world ? world.level() : player.getServer().overworld();
        result.setReturnValue(new DimensionTransition(level,
                Vec3.atBottomCenterOf(player.adjustSpawnLocation(level, level.getSharedSpawnPos())), Vec3.ZERO,
                level.getSharedSpawnAngle(), 0F, after));
    }

    @ModifyArg(method = "findRespawnPositionAndUseSpawnBlock", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/portal/DimensionTransition;missingRespawnBlock(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/portal/DimensionTransition$PostDimensionTransition;)Lnet/minecraft/world/level/portal/DimensionTransition;"))
    private ServerLevel golem$missingSpawn(ServerLevel vanilla) {
        var destination = Respawns.FALLBACKS.invoker().destination((ServerPlayer) (Object) this);
        return destination instanceof Respawns.WorldSpawn world ? world.level() : vanilla;
    }
    *///?}

    //? if <26.1 {
    /*@ModifyExpressionValue(method = BED_LOOKUP, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/dimension/DimensionType;natural()Z"))
    private boolean golem$bedWorld(boolean natural, @Local(argsOnly = true) BlockPos pos) {
        var level = ((ServerPlayer) (Object) this).level();
        return WorldAttributes.BED_SPAWN.invoker().apply(level, pos, natural)
                || WorldAttributes.BED_SLEEP.invoker().apply(level, pos, natural);
    }
    *///?}

    //? if !fabric && <26.1 {
    /*@WrapOperation(method = BED_LOOKUP, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;setRespawnPosition(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;FZZ)V"))
    private void golem$bedSpawn(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos, float yaw,
            boolean forced, boolean message, Operation<Void> original) {
        if (WorldAttributes.BED_SPAWN.invoker().apply(player.level(), pos, player.level().dimensionType().natural()))
            original.call(player, dimension, pos, yaw, forced, message);
    }
    *///?}
}
