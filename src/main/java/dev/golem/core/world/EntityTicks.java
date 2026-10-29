package dev.golem.core.world;

import dev.golem.core.hook.Hook;

import java.util.function.Consumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * After a server entity, passengers included, completes its tick; a vehicle is notified before its passengers tick.
 * On NeoForge a passenger is notified before its vehicle moves it to its seat, elsewhere after.
 */
public final class EntityTicks {
    public static final Hook<Consumer<Entity>> AFTER = Hook.notifying();

    static {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) -> {
                    var entity = event.getEntity();
                    if (entity.level() instanceof ServerLevel) ticked(entity);
                });
        //?}
    }

    private EntityTicks() {
    }

    public static void ticked(Entity entity) {
        for (var listener : AFTER) listener.accept(entity);
    }
}
