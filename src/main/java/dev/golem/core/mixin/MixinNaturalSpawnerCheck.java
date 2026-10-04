package dev.golem.core.mixin;

import dev.golem.core.world.Spawns;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric's {@link Spawns#onSpawnCheck} hook: asks the registered vetoes at the two places Forge-like loaders fire
 * {@code MobSpawnEvent.PositionCheck} - the natural spawner's position check and the chunk-generation spawn.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla has no runtime spawn veto - spawn placements and biome spawn
 * lists are static; (2) Fabric API has no spawn event (Forge and NeoForge use their {@code PositionCheck} event,
 * so this class is listed only in {@code golemcore.fabric.mixins.json}); (3) widening cannot help, the answer has
 * to change inside the spawner. The natural check is a HEAD inject on {@code isValidPositionForMob}; the
 * generation spawn has no method of its own, so its {@code Mob.checkSpawnRules} call is redirected (an inject on
 * {@code Mob.checkSpawnRules} would miss every subclass that overrides it without calling super).
 */
@Mixin(NaturalSpawner.class)
public abstract class MixinNaturalSpawnerCheck {

    @Inject(method = "isValidPositionForMob", at = @At("HEAD"), cancellable = true)
    private static void golem$naturalSpawnCheck(ServerLevel level, Mob mob, double distance,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (Spawns.denied(mob, level, Spawns.Reason.NATURAL)) cir.setReturnValue(false);
    }

    //? if >=26.1 {
    @Redirect(method = "spawnMobsForChunkGeneration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;checkSpawnRules(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;)Z"))
    private static boolean golem$generationSpawnCheck(Mob mob, LevelAccessor level,
                                                      net.minecraft.world.entity.EntitySpawnReason reason) {
        if (level instanceof ServerLevelAccessor accessor
                && Spawns.denied(mob, accessor.getLevel(), Spawns.Reason.CHUNK_GENERATION)) return false;
        return mob.checkSpawnRules(level, reason);
    }
    //?} else {
    /*@Redirect(method = "spawnMobsForChunkGeneration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;checkSpawnRules(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/MobSpawnType;)Z"))
    private static boolean golem$generationSpawnCheck(Mob mob, LevelAccessor level,
                                                      net.minecraft.world.entity.MobSpawnType reason) {
        if (level instanceof ServerLevelAccessor accessor
                && Spawns.denied(mob, accessor.getLevel(), Spawns.Reason.CHUNK_GENERATION)) return false;
        return mob.checkSpawnRules(level, reason);
    }
    *///?}
}
