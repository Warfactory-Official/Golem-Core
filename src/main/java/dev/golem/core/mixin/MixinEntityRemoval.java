package dev.golem.core.mixin;

import dev.golem.core.world.EntityRemovals;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Loader unload events follow tracking visibility and can miss untracked entities.
// Vanilla has no removal callback; widening cannot notify after its world callback completes.
@Mixin(Entity.class)
public abstract class MixinEntityRemoval {
    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void golem$entityRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        EntityRemovals.removed((Entity) (Object) this);
    }
}
