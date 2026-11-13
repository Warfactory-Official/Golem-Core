package dev.golem.core.mixin;

import dev.golem.core.world.Spawns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link Spawns#onPotentialSpawns}: once per spawn group, where 1.7's {@code spawnRandomCreature} fired
 * {@code WorldEvent.PotentialSpawns}. Why a mixin (AGENTS.md section 7): vanilla has no hook here, and the loaders'
 * {@code PotentialSpawns} events fire from {@code mobsAt}, which every later try of a group reads again; the
 * signature is the same on 1.20.1, 1.21.1 and 26.1, and nothing to widen helps.
 */
@Mixin(NaturalSpawner.class)
public abstract class MixinSpawnGroupPick {

    @Inject(method = "getRandomSpawnMobAt", at = @At("HEAD"))
    private static void golem$potentialSpawns(ServerLevel level, StructureManager structures, ChunkGenerator generator,
                                              MobCategory category, RandomSource random, BlockPos pos,
                                              CallbackInfoReturnable<?> cir) {
        Spawns.potentialSpawns(level, category, pos);
    }
}
