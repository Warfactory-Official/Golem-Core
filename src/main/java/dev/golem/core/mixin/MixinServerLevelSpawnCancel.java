package dev.golem.core.mixin;

import dev.golem.core.world.Spawns;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric's other half of {@link Spawns.Reason#WORLD_SPAWNER}: a mob whose spawn a check refused while it was finalized
 * ({@link MixinMobFinalizeSpawnCheck}) is not added - what NeoForge's built-in {@code EntityJoinLevelEvent} blocker and
 * Forge's {@code addFreshEntity} check do with a cancelled spawn. {@code addFreshEntityWithPassengers} adds through
 * this method too.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla has no cancellable add; (2) Fabric API's
 * {@code ServerEntityEvents.ENTITY_LOAD} runs after the entity is in the level and cannot refuse it (Forge-like
 * loaders need nothing, so this class is listed only in {@code golemcore.fabric.mixins.json}); (3) widening cannot
 * help. One cancellable HEAD {@code @Inject}; it returns {@code false}, the value vanilla returns for a refused add.
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevelSpawnCancel {

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void golem$dropCancelledSpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Spawns.cancelledSpawn(entity)) cir.setReturnValue(false);
    }
}
