package dev.golem.core.mixin;

import dev.golem.core.world.EntityTicks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla exposes no after-entity-tick callback; NeoForge supplies EntityTickEvent.Post.
 * Fabric and Forge have no equivalent, and widening cannot insert this notification.
 */
@Mixin(ServerLevel.class)
public abstract class MixinEntityTicks {
    //? if !neoforge {
    /*@Inject(method = "tickNonPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER))
    private void golem$entityTicked(Entity entity, CallbackInfo callback) {
        EntityTicks.ticked(entity);
    }

    @Inject(method = "tickPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;rideTick()V", shift = At.Shift.AFTER))
    private void golem$passengerTicked(Entity vehicle, Entity passenger, CallbackInfo callback) {
        EntityTicks.ticked(passenger);
    }
    *///?}
}
