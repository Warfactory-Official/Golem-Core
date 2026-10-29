package dev.golem.core.world;

import dev.golem.core.hook.Hook;

import java.util.function.Consumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Observes actual server removal after its reason and world bookkeeping have been applied.
 */
public final class EntityRemovals {
    public static final Hook<Consumer<Entity>> AFTER = Hook.notifying();

    private EntityRemovals() {
    }

    public static void removed(Entity entity) {
        if (!(entity.level() instanceof ServerLevel)) return;
        for (var listener : AFTER) listener.accept(entity);
    }
}
