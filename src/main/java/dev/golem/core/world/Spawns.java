package dev.golem.core.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/**
 * Running a freshly constructed mob's spawn initialisation — 1.7 {@code onSpawnWithEgg(null)} — from outside
 * the mob, across the {@code finalizeSpawn} signature changes: 1.20.1 took a trailing NBT argument, 1.21.1
 * dropped it, 26.1 renamed the spawn-reason type.
 */
public final class Spawns {

    private Spawns() {}

    /** Equips and initialises {@code mob} as a triggered spawn at its current position (1.7 {@code onSpawnWithEgg}). */
    public static void finalizeTriggered(Mob mob, ServerLevel level) {
        //? if >=26.1 {
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                net.minecraft.world.entity.EntitySpawnReason.TRIGGERED, null);
        //?} else if >=1.21.1 {
        /*mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.TRIGGERED, null);
        *///?} else {
        /*mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.TRIGGERED, null, null);
        *///?}
    }
}
