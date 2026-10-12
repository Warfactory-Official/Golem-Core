package dev.golem.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@link dev.golem.core.world.Furnaces#IGNITION}: the burn time a vanilla furnace takes from a fuel item, at its
 * position.
 *
 * <p>Why a mixin (§7). Rung 1, vanilla: the burn time is {@code getBurnDuration}, which a furnace computes from the
 * fuel alone; no override point sees the furnace's level and position. Rung 2, loaders: NeoForge and Forge fire
 * {@code FurnaceFuelBurnTimeEvent} for the fuel only (no position, and it also answers recipe viewers and hoppers),
 * Fabric has no event at all. Rung 3, wideners: visibility is not the problem. The smallest mixin is one
 * {@code @WrapOperation} around that single call inside {@code serverTick}, which keeps vanilla's fuel consumption and
 * lit-state logic untouched (a zero burn time is how vanilla already treats a non-fuel).
 *
 * <p>The target spellings are per era: 26.1's {@code serverTick} takes a {@code ServerLevel} and burn times come from
 * {@code FuelValues}.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class MixinFurnaceIgnition {

    //? if >=26.1 {
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;getBurnDuration(Lnet/minecraft/world/level/block/entity/FuelValues;Lnet/minecraft/world/item/ItemStack;)I"))
    private static int golem$ignition(AbstractFurnaceBlockEntity furnace,
                                      net.minecraft.world.level.block.entity.FuelValues values, ItemStack fuel,
                                      Operation<Integer> original,
                                      @Local(argsOnly = true) net.minecraft.server.level.ServerLevel level,
                                      @Local(argsOnly = true) BlockPos pos) {
        return dev.golem.core.world.Furnaces.burnDuration(level, pos, fuel, original.call(furnace, values, fuel));
    }
    //?} else {
    /*@WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;getBurnDuration(Lnet/minecraft/world/item/ItemStack;)I"))
    private static int golem$ignition(AbstractFurnaceBlockEntity furnace, ItemStack fuel, Operation<Integer> original,
                                      @Local(argsOnly = true) net.minecraft.world.level.Level level,
                                      @Local(argsOnly = true) BlockPos pos) {
        return dev.golem.core.world.Furnaces.burnDuration(level, pos, fuel, original.call(furnace, fuel));
    }
    *///?}
}
