package dev.golem.core.world;

import java.util.function.BiConsumer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Initial state sent when an observer starts tracking an entity.
 */
public final class Tracking {
    private Tracking() {
    }

    public static void onStart(BiConsumer<Entity, ServerPlayer> handler) {
        //? if fabric {
        /*net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register(handler::accept);
         *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) ->
                        handler.accept(event.getTarget(), (ServerPlayer) event.getEntity()));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) ->
                        handler.accept(event.getTarget(), (ServerPlayer) event.getEntity()));
        *///?}
    }
}
