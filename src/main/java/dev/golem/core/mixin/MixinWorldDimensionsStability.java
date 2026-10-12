package dev.golem.core.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link dev.golem.core.world.Dimensions#dimension}: a dimension a mod declared is stable, so a world that has it gets
 * no "experimental settings" confirmation on create or open.
 *
 * <p>Why a mixin (§7). Rung 1, vanilla: {@code WorldDimensions.checkStability} calls every dimension that is not one of
 * the three vanilla ones experimental, and nothing a datapack or a mod registers changes that answer. Rung 2, loaders:
 * Fabric API has this rule (its own {@code WorldDimensionsMixin}); NeoForge and Forge have no event or option for it.
 * Rung 3, wideners: visibility is not the problem. The smallest mixin is one cancellable {@code @Inject} at the head
 * of that one static method, answering only for declared dimensions; every other dimension keeps vanilla's answer.
 * The method has the same signature on 1.20.1, 1.21.1 and 26.1.
 *
 * <p>The same injection calls a declared world type's overworld ({@link dev.golem.core.world.WorldTypes}: vanilla's but
 * for its biome source's parameter list name) stable.
 *
 * <p>Before 26.1 {@code bake} also calls the whole dimension registry experimental unless it holds exactly the vanilla
 * three ({@code list.size() == VANILLA_DIMENSION_COUNT}; 26.1 asks whether the vanilla three are present instead). The
 * second injection, on that one {@code size()} call, leaves the declared dimensions out of the count.
 */
@Mixin(WorldDimensions.class)
public abstract class MixinWorldDimensionsStability {

    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void golem$declaredStable(ResourceKey<LevelStem> key, LevelStem stem,
                                             CallbackInfoReturnable<Lifecycle> cir) {
        if (dev.golem.core.world.Dimensions.isDeclared(key) || dev.golem.core.world.WorldTypes.isStableOverworld(key, stem)) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }

    //? if <26.1 {
    /*@com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "bake", at = @At(value = "INVOKE",
            target = "Ljava/util/List;size()I"))
    private int golem$declaredNotCounted(int size,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) net.minecraft.core.Registry<LevelStem> registry) {
        java.util.Set<ResourceKey<LevelStem>> keys = new java.util.HashSet<>(registry.registryKeySet());
        //? if >=1.21 {
        /^keys.addAll(((WorldDimensions) (Object) this).dimensions().keySet());
        ^///?} else {
        /^keys.addAll(((WorldDimensions) (Object) this).dimensions().registryKeySet());
        ^///?}
        int declared = 0;
        for (ResourceKey<LevelStem> key : keys) if (dev.golem.core.world.Dimensions.isDeclared(key)) declared++;
        return size - declared;
    }
    *///?}
}
