package dev.golem.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.golem.core.world.FlatWorlds;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.stream.Stream;

/**
 * {@link FlatWorlds}: adds the named namespaces' structure sets to a flat world's. Why a mixin (AGENTS.md section 7):
 * the preset list is saved with the world, no loader event sees it, and widening changes nothing. One
 * {@code @ModifyArg} on the stream {@code createState} hands {@code createForFlat}, the same on 1.20.1, 1.21.1 and 26.1.
 */
@Mixin(FlatLevelSource.class)
public abstract class MixinFlatStructureSets {

    //? if >=26.3 {
    /*@ModifyArg(method = "createState", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;createForFlat(Lnet/minecraft/world/level/levelgen/RandomState;JLnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/biome/BiomeSource;Ljava/util/stream/Stream;)Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;"),
            index = 4)
    *///?} else {
    @ModifyArg(method = "createState", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;createForFlat(Lnet/minecraft/world/level/levelgen/RandomState;JLnet/minecraft/world/level/biome/BiomeSource;Ljava/util/stream/Stream;)Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;"),
            index = 3)
    //?}
    private Stream<Holder<StructureSet>> golem$modStructureSets(Stream<Holder<StructureSet>> preset,
                                                                 @Local(argsOnly = true) HolderLookup<StructureSet> lookup) {
        return FlatWorlds.withModSets(preset, lookup);
    }
}
