package dev.golem.core.mixin;

import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code processPendingLoads} for {@code Chunks.loadEntityTicking} before 26.1. An invoker, not an access transformer:
 * widening the method to public breaks Canary's private {@code @Overwrite} of it, and the server fails to load a level.
 */
@Mixin(PersistentEntitySectionManager.class)
public interface EntitySectionsAccess {

    @Invoker("processPendingLoads")
    void golem$processPendingLoads();
}
