package dev.golem.core.harness.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Vanilla and loaders expose no GameTest dimension-registry selection hook. Dev-only: the harness config is stripped.
// Widening cannot replace the flat preset's registry with the loaded datapack registry.
@Mixin(GameTestServer.class)
public abstract class MixinGameTestDimensions {
    // '*': the WorldLoader data lambda in create(), whose synthetic name differs between mappings
    @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/MappedRegistry;freeze()Lnet/minecraft/core/Registry;"))
    private static Registry<LevelStem> golem$datapackDimensions(Registry<LevelStem> original,
                                                                @Local(argsOnly = true) WorldLoader.DataLoadContext context) {
        if (!Boolean.getBoolean("golem.gametest.datapackDimensions") || original.key() != Registries.LEVEL_STEM)
            return original;
        //? if >=26.1 {
        return context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM);
        //?} else {
        /*return context.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM);
         *///?}
    }
}
