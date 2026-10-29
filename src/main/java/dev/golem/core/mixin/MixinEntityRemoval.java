package dev.golem.core.mixin;

import dev.golem.core.world.EntityRemovals;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Loader leave events (EntityLeaveLevelEvent, ServerEntityEvents.ENTITY_UNLOAD) fire when tracking ends: a chunk
// unload ends it before any removal reason is set, and the later UNLOADED_TO_CHUNK removal fires nothing.
// Vanilla has no removal callback and widening cannot add one.
@Mixin(Entity.class)
public abstract class MixinEntityRemoval {
    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void golem$entityRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        EntityRemovals.removed((Entity) (Object) this);
    }
}
