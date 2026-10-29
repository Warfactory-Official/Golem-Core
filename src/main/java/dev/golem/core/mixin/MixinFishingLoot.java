package dev.golem.core.mixin;

import dev.golem.core.world.FishingLoot;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// Vanilla has no context-sensitive category selector; loader loot modifiers run after RNG consumption.
// Widening exposes the pools but cannot replace them for one draw before that consumption.
@Mixin(LootTable.class)
public abstract class MixinFishingLoot {
    @Unique
    private byte golem$fishingCategory;

    //? if >=1.21 || forge {
    @ModifyExpressionValue(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/storage/loot/LootTable;pools:Ljava/util/List;"))
    private List<LootPool> golem$category(List<LootPool> pools, LootContext context, Consumer<ItemStack> output,
                                          @Local(ordinal = 1) Consumer<ItemStack> decoratedOutput) {
        return golem$replaceCategory(context, decoratedOutput) ? List.of() : pools;
    }
    //?} else {
    /*@Unique
    private static final LootPool[] golem$NO_POOLS = new LootPool[0];

    @ModifyExpressionValue(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/storage/loot/LootTable;pools:[Lnet/minecraft/world/level/storage/loot/LootPool;"))
    private LootPool[] golem$category(LootPool[] pools, LootContext context, Consumer<ItemStack> output,
                                     @Local(ordinal = 1) Consumer<ItemStack> decoratedOutput) {
        return golem$replaceCategory(context, decoratedOutput) ? golem$NO_POOLS : pools;
    }
    *///?}

    @Unique
    private boolean golem$replaceCategory(LootContext context, Consumer<ItemStack> decoratedOutput) {
        if (FishingLoot.SELECTORS.isEmpty() || (Object) this == LootTable.EMPTY) return false;
        if (golem$fishingCategory == 0) {
            golem$fishingCategory = FishingLoot.categoryTag((LootTable) (Object) this, context.getLevel());
        }
        if (golem$fishingCategory < 0) return false;
        //? if >=26.1 {
        var origin = context.getOptionalParameter(LootContextParams.ORIGIN);
        //?} else {
        /*var origin = context.getParamOrNull(LootContextParams.ORIGIN);
         *///?}
        var replacement = FishingLoot.select(context.getLevel(), origin == null ? null : BlockPos.containing(origin),
                golem$fishingCategory, context.getRandom());
        if (replacement == null) return false;
        decoratedOutput.accept(replacement);
        return true;
    }
}
