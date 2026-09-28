package dev.golem.core.mixin;

import dev.golem.core.world.CompatRailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Calls {@link CompatRailBlock#getRailMaxSpeed} and {@link CompatRailBlock#onMinecartPass} on the nodes whose
 * loader no longer (NeoForge 26.1) or never (Fabric) routes a cart through them.
 *
 * <p>Why a mixin: (1) vanilla has no per-rail speed or pass hook at all — the cart's cap is a constant in
 * {@code getMaxSpeed}; (2) NeoForge 26.1 declares {@code IBaseRailBlockExtension.getRailMaxSpeed/onMinecartPass}
 * but nothing calls them any more, and Fabric API has no minecart events; (3) widening does not help, the value
 * has to change inside the cart's own tick. The injections are the smallest that do it: one return-value
 * override on the speed query and one call after the cart's rail step. Forge 1.20.1 and NeoForge 1.21.1 still
 * call both methods themselves (Forge {@code moveMinecartOnRail}/{@code moveAlongTrack}), so there this class
 * is empty, is not listed in the mixin config, and the loader does the work.
 *
 * <p>The pass is the loader's, placed as the loader placed it: once per server tick, for the rail (and state)
 * the cart stood on when its rail step began, even if the step carries it off that rail. On 26.1 it runs right
 * after the cart's {@code behavior.tick()}, which is where the rail step ends. Under 26.1's experimental
 * minecart movement a rail's speed is applied as a ratio to the game-rule speed (see
 * {@code golem$railMaxSpeed}), so default-speed rails leave experimental carts exactly vanilla.
 *
 * <p>Listed in {@code golemcore.mixins.json} through {@code ${rail_mixins}} only on the nodes that need it.
 */
@Mixin(AbstractMinecart.class)
public abstract class MixinMinecartRails {

    //? if >=26.1 {
    @Unique
    private BlockPos golem$railPos;
    @Unique
    private BlockState golem$railState;

    @Inject(method = "getMaxSpeed(Lnet/minecraft/server/level/ServerLevel;)D", at = @At("RETURN"), cancellable = true)
    private void golem$railMaxSpeed(ServerLevel level, CallbackInfoReturnable<Double> cir) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        if (!AbstractMinecart.useExperimentalMovement(level)) {
            golem$applyRailSpeed(cir);
            return;
        }
        // Experimental NewMinecartBehavior: the cart's cap is the maxMinecartSpeed game rule, not a fixed
        // 0.4 blocks/tick, so an absolute Forge rail speed would clamp every experimental cart to 1.12
        // speeds. There a rail's max speed means its ratio to the vanilla rail default: a rail that keeps
        // the default leaves the game-rule speed untouched, a faster or slower one scales it the same way
        // it scales a legacy cart (the Forge 1.2 cap included, expressed in the same ratio).
        BlockPos pos = self.getCurrentBlockPosOrRailBelow();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CompatRailBlock rail) {
            double vanilla = self.isInWater() ? 0.2 : 0.4;
            double railSpeed = Math.min(rail.getRailMaxSpeed(state, level, pos, self), CompatRailBlock.CART_SPEED_CAP);
            cir.setReturnValue(cir.getReturnValue() * railSpeed / vanilla);
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void golem$captureRail(CallbackInfo ci) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        golem$railPos = null;
        golem$railState = null;
        if (!(self.level() instanceof ServerLevel)) return;
        BlockPos pos = self.getCurrentBlockPosOrRailBelow();
        BlockState state = self.level().getBlockState(pos);
        // Forge only reached onMinecartPass from moveAlongTrack, i.e. when the cart started the tick on a rail
        if (state.getBlock() instanceof CompatRailBlock) {
            golem$railPos = pos;
            golem$railState = state;
        }
    }

    @Inject(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/minecart/MinecartBehavior;tick()V", shift = At.Shift.AFTER))
    private void golem$railPass(CallbackInfo ci) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        BlockPos pos = golem$railPos;
        BlockState state = golem$railState;
        golem$railPos = null;
        golem$railState = null;
        // Forge called the pass at the end of moveAlongTrack with the rail and state the step began on, even
        // when the step carried the cart off that rail, so neither isOnRails nor the rail's current state is
        // consulted here.
        if (pos != null && state.getBlock() instanceof CompatRailBlock rail) rail.onMinecartPass(state, self.level(), pos, self);
    }
    //?} else if fabric {
    /*@Inject(method = "getMaxSpeed()D", at = @At("RETURN"), cancellable = true)
    private void golem$railMaxSpeed(CallbackInfoReturnable<Double> cir) {
        golem$applyRailSpeed(cir);
    }

    @Inject(method = "moveAlongTrack(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("TAIL"))
    private void golem$railPass(BlockPos pos, BlockState state, CallbackInfo ci) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        if (state.getBlock() instanceof CompatRailBlock rail) rail.onMinecartPass(state, self.level(), pos, self);
    }
    *///?}

    //? if >=26.1 || fabric {
    @Unique
    private void golem$applyRailSpeed(CallbackInfoReturnable<Double> cir) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        BlockPos pos = CompatRailBlock.railPosition(self);
        BlockState state = self.level().getBlockState(pos);
        if (state.getBlock() instanceof CompatRailBlock rail) {
            // Forge getMaxSpeedWithRail: the rail's cap, never past the cart's own rail cap
            cir.setReturnValue((double) Math.min(rail.getRailMaxSpeed(state, self.level(), pos, self),
                    CompatRailBlock.CART_SPEED_CAP));
        }
    }
    //?}
}
