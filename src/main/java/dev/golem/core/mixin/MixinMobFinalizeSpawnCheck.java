package dev.golem.core.mixin;

import dev.golem.core.world.Spawns;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric's half of {@link Spawns.Reason#WORLD_SPAWNER}: asks the spawn checks at the head of {@code Mob.finalizeSpawn},
 * the call every vanilla spawner makes before adding a mob and the one Forge-like loaders turn into their
 * {@code FinalizeSpawn} event. A refused spawn is dropped when it is added ({@link MixinServerLevelSpawnCancel}), as
 * the Forge-like loaders drop a mob whose spawn was cancelled.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla has no runtime veto - {@code PatrolSpawner}, {@code PhantomSpawner}
 * and {@code WanderingTraderSpawner} create, finalize and add their mobs with no callback; (2) Fabric API has no spawn
 * event (Forge and NeoForge use {@code FinalizeSpawn}, so this class is listed only in
 * {@code golemcore.fabric.mixins.json}); (3) widening cannot help, the answer has to change inside the spawn. One HEAD
 * {@code @Inject} that reads the arguments and cancels nothing; subclasses that override {@code finalizeSpawn} call
 * super (every vanilla patrol, phantom, trader and llama class does).
 */
@Mixin(Mob.class)
public abstract class MixinMobFinalizeSpawnCheck {

    //? if >=26.1 {
    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void golem$worldSpawnerCheck(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
                                         net.minecraft.world.entity.EntitySpawnReason reason,
                                         net.minecraft.world.entity.SpawnGroupData data,
                                         CallbackInfoReturnable<net.minecraft.world.entity.SpawnGroupData> cir) {
        Mob mob = (Mob) (Object) this;
        Spawns.finalizing(mob, level, Spawns.worldSpawnerReason(mob, reason));
    }
    //?} else if >=1.20.5 {
    /*@Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void golem$worldSpawnerCheck(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
                                         net.minecraft.world.entity.MobSpawnType reason,
                                         net.minecraft.world.entity.SpawnGroupData data,
                                         CallbackInfoReturnable<net.minecraft.world.entity.SpawnGroupData> cir) {
        Mob mob = (Mob) (Object) this;
        Spawns.finalizing(mob, level, Spawns.worldSpawnerReason(mob, reason));
    }
    *///?} else {
    /*@Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void golem$worldSpawnerCheck(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
                                         net.minecraft.world.entity.MobSpawnType reason,
                                         net.minecraft.world.entity.SpawnGroupData data,
                                         net.minecraft.nbt.CompoundTag tag,
                                         CallbackInfoReturnable<net.minecraft.world.entity.SpawnGroupData> cir) {
        Mob mob = (Mob) (Object) this;
        Spawns.finalizing(mob, level, Spawns.worldSpawnerReason(mob, reason));
    }
    *///?}
}
