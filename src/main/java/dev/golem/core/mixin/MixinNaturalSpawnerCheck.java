package dev.golem.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.golem.core.world.Spawns;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric's {@link Spawns#onSpawnCheck} hook: asks the registered vetoes at the two places Forge-like loaders fire
 * {@code MobSpawnEvent.PositionCheck} - the natural spawner's position check and the chunk-generation spawn.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla has no runtime spawn veto - spawn placements and biome spawn
 * lists are static; (2) Fabric API has no spawn event (Forge and NeoForge use their {@code PositionCheck} event,
 * so this class is listed only in {@code golemcore.fabric.mixins.json}); (3) widening cannot help, the answer has
 * to change inside the spawner. The natural check is a HEAD inject on {@code isValidPositionForMob}; the
 * generation spawn has no method of its own, so its {@code Mob.checkSpawnRules} call is wrapped (an inject on
 * {@code Mob.checkSpawnRules} would miss every subclass that overrides it without calling super). A MixinExtras
 * {@code @WrapOperation}, not a {@code @Redirect}: only one mod may redirect a call, while wrappers chain, and
 * spawn-control mods hook this same call.
 */
@Mixin(NaturalSpawner.class)
public abstract class MixinNaturalSpawnerCheck {

    /**
     * {@link Spawns#onPotentialSpawns}: Forge-like loaders fire {@code LevelEvent.PotentialSpawns} from this method; the
     * arguments are the same on every supported version (only the returned list type changed), so one HEAD inject
     * serves all three Fabric nodes. Same justification as the class: no vanilla or Fabric hook, nothing to widen.
     */
    @Inject(method = "mobsAt", at = @At("HEAD"))
    private static void golem$potentialSpawns(ServerLevel level, net.minecraft.world.level.StructureManager structureManager,
                                              net.minecraft.world.level.chunk.ChunkGenerator generator,
                                              net.minecraft.world.entity.MobCategory category, net.minecraft.core.BlockPos pos,
                                              net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome,
                                              CallbackInfoReturnable<?> cir) {
        Spawns.potentialSpawns(level, category, pos);
    }

    @Inject(method = "isValidPositionForMob", at = @At("HEAD"), cancellable = true)
    private static void golem$naturalSpawnCheck(ServerLevel level, Mob mob, double distance,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (Spawns.denied(mob, level, Spawns.Reason.NATURAL)) cir.setReturnValue(false);
    }

    //? if >=26.1 {
    @WrapOperation(method = "spawnMobsForChunkGeneration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;checkSpawnRules(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;)Z"))
    private static boolean golem$generationSpawnCheck(Mob mob, LevelAccessor level,
                                                      net.minecraft.world.entity.EntitySpawnReason reason,
                                                      Operation<Boolean> original) {
        if (level instanceof ServerLevelAccessor accessor
                && Spawns.denied(mob, accessor.getLevel(), Spawns.Reason.CHUNK_GENERATION)) return false;
        return original.call(mob, level, reason);
    }
    //?} else {
    /*@WrapOperation(method = "spawnMobsForChunkGeneration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;checkSpawnRules(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/MobSpawnType;)Z"))
    private static boolean golem$generationSpawnCheck(Mob mob, LevelAccessor level,
                                                      net.minecraft.world.entity.MobSpawnType reason,
                                                      Operation<Boolean> original) {
        if (level instanceof ServerLevelAccessor accessor
                && Spawns.denied(mob, accessor.getLevel(), Spawns.Reason.CHUNK_GENERATION)) return false;
        return original.call(mob, level, reason);
    }
    *///?}
}
