package dev.golem.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@link dev.golem.core.world.Evaporation#WATER}: whether water emptied from a bucket evaporates, at its position.
 *
 * <p>Why a mixin (§7). Rung 1, vanilla: the decision is one expression inside {@code BucketItem.emptyContents}, read
 * from the dimension type (before 26.1) or the {@code water_evaporates} environment attribute (26.1), neither of which a
 * mod can answer per position; a dispenser's water bucket goes through the same method. Rung 2, loaders: NeoForge
 * removed {@code FillBucketEvent} (absent in 21.1 and 26.1), Forge 1.20.1's fires only for a player's bucket
 * and not for a dispenser, Fabric has no bucket event, and NeoForge/Forge's {@code FluidType.isVaporizedOnPlacement}
 * belongs to the water fluid type the loader owns. Rung 3, wideners: visibility is not the problem. The smallest mixin is
 * one {@code @WrapOperation} around that single query, so everything else (the hiss, the smoke, the emptied bucket,
 * placement) stays vanilla's.
 *
 * <p>The query is spelled per era: 26.1 reads {@code EnvironmentAttributeSystem.getValue(WATER_EVAPORATES, pos)} (an
 * {@code Object}, unboxed by the caller), 1.20.1 and 1.21.1 {@code DimensionType.ultraWarm()}. The trailing star of
 * {@code "emptyContents*"} matches both overloads (a bare name selects only the first): on Forge-like loaders the query
 * sits in the stack-aware one, on Fabric in vanilla's only one. Forge 1.20.1 is the exception: its runtime is SRG-named,
 * so the refmap turns the pattern into vanilla's {@code m_142073_} alone, which only delegates to Forge's stack-aware
 * {@code emptyContents} (a Forge-added name, the same in every namespace); that overload is named explicitly. Listed
 * in {@code golemcore.mixins.json} (every loader).
 */
@Mixin(BucketItem.class)
public abstract class MixinBucketEvaporation {

    //? if <26.1 && forge {
    /*@WrapOperation(method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/DimensionType;ultraWarm()Z"))
    private boolean golem$evaporates(net.minecraft.world.level.dimension.DimensionType type, Operation<Boolean> original,
                                     @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        return dev.golem.core.world.Evaporation.water(level, pos, original.call(type));
    }
    *///?} else if <26.1 {
    /*@WrapOperation(method = "emptyContents*", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/dimension/DimensionType;ultraWarm()Z"))
    private boolean golem$evaporates(net.minecraft.world.level.dimension.DimensionType type, Operation<Boolean> original,
                                     @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        return dev.golem.core.world.Evaporation.water(level, pos, original.call(type));
    }
    *///?}
}
