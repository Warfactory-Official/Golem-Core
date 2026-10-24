package dev.golem.core.platform;

import dev.golem.core.hook.Hook;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Server lifecycle callbacks, under one set of names on every loader and every version.
 *
 * <p>fabric-api renamed several of its own events between the 1.21.1 and 26.1 lines:
 * {@code ServerWorldEvents} became {@code ServerLevelEvents}, and {@code CHUNK_LOAD} has a third
 * "newly generated" parameter, changing its SAM. That is not a Minecraft difference, so a consumer
 * should not see it.
 *
 * <p>NeoForge and Forge are separate implementations rather than one {@code forgelike} one: the event
 * package moved from {@code net.minecraftforge} to {@code net.neoforged}, and the tick event is one
 * class with a phase field on Forge and separate Pre/Post classes on NeoForge.
 */
public final class Lifecycle {

    private Lifecycle() {}

    /**
     * An entity was added to a server level: loaded with its chunk, spawned, or arrived from another level (a player
     * changing dimension or respawning is reported for the level it arrives in). Listeners see it already in the level.
     */
    public static final Hook<BiConsumer<Entity, ServerLevel>> ENTITY_LOAD =
            Hook.notifying();
    private static boolean entityLoadHooked;

    /** Registers an {@link #ENTITY_LOAD} listener. */
    public static synchronized void onEntityLoad(BiConsumer<Entity, ServerLevel> handler) {
        ENTITY_LOAD.register(handler);
        if (entityLoadHooked) return;
        entityLoadHooked = true;
        //? if fabric {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register(Lifecycle::entityLoaded);
        // A player moved between levels or respawned is not always reported as loaded; report it for its new level.
        //? if >=26.1 {
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
                (player, origin, destination) -> entityLoaded(player, destination));
        //?} else {
        /^net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> entityLoaded(player, destination));
        ^///?}
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> entityLoaded(newPlayer, (ServerLevel) newPlayer.level()));
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) -> {
                    if (event.getLevel() instanceof ServerLevel level) entityLoaded(event.getEntity(), level);
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.EntityJoinLevelEvent event) -> {
                    if (event.getLevel() instanceof ServerLevel level) entityLoaded(event.getEntity(), level);
                });
        *///?}
    }

    private static void entityLoaded(net.minecraft.world.entity.Entity entity, ServerLevel level) {
        for (BiConsumer<net.minecraft.world.entity.Entity, ServerLevel> listener : ENTITY_LOAD) listener.accept(entity, level);
    }

    /** Runs at the end of every server tick. */
    public static void onServerTick(Consumer<MinecraftServer> handler) {
        //? if fabric {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(handler::accept);
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> handler.accept(event.getServer()));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.ServerTickEvent event) -> {
                    if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) handler.accept(event.getServer());
                });
        *///?}
    }

    /** Runs when a level is unloaded, before its chunks are gone. */
    public static void onLevelUnload(BiConsumer<MinecraftServer, ServerLevel> handler) {
        //? if fabric && >=26.1 {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.UNLOAD.register(handler::accept);
        *///?} elif fabric {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.UNLOAD.register(handler::accept);
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.LevelEvent.Unload event) -> {
                    if (event.getLevel() instanceof ServerLevel level) handler.accept(level.getServer(), level);
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.level.LevelEvent.Unload event) -> {
                    if (event.getLevel() instanceof ServerLevel level) handler.accept(level.getServer(), level);
                });
        *///?}
    }

    /** Runs when a chunk finishes loading on the server. */
    public static void onChunkLoad(BiConsumer<ServerLevel, LevelChunk> handler) {
        //? if fabric && >=26.1 {
        /*// 26.1's fabric-api added a "newly generated" flag, so the SAM takes three arguments here and
        // two below. The flag is dropped rather than surfaced: no consumer has needed it, and adding a
        // parameter nobody can supply on the older line would defeat the point of the facade.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD
                .register((level, chunk, newlyGenerated) -> handler.accept(level, chunk));
        *///?} elif fabric {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD
                .register((level, chunk) -> handler.accept(level, chunk));
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.ChunkEvent.Load event) -> {
                    if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
                        handler.accept(level, chunk);
                    }
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.level.ChunkEvent.Load event) -> {
                    if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
                        handler.accept(level, chunk);
                    }
                });
        *///?}
    }

    /** Runs when a chunk is unloaded on the server. */
    public static void onChunkUnload(BiConsumer<ServerLevel, LevelChunk> handler) {
        //? if fabric {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_UNLOAD
                .register((level, chunk) -> handler.accept(level, chunk));
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.ChunkEvent.Unload event) -> {
                    if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
                        handler.accept(level, chunk);
                    }
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.level.ChunkEvent.Unload event) -> {
                    if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
                        handler.accept(level, chunk);
                    }
                });
        *///?}
    }
    //? if fabric {
    /*/^* The Fabric COPY_FROM phase edge is added once, however many handlers register. ^/
    private static boolean playerCopyPhaseOrdered;
    *///?}

    /**
     * Runs when a server player is replaced by a fresh copy - respawn after death ({@code alive} false) or the
     * return from the End ({@code alive} true) - with the old and the new player. Forge/NeoForge
     * {@code PlayerEvent.Clone}, Fabric {@code ServerPlayerEvents.COPY_FROM}: state a mod keeps on the player
     * object itself is lost unless it is copied here.
     *
     * <p>Handlers run after the loader's own copying is finished, so {@link dev.golem.core.data.EntityData}
     * on the new player is always fresh here: it is never carried over by the loader, only by a handler.
     */
    public static void onPlayerCopy(PlayerCopy handler) {
        //? if fabric {
        /*var event = net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.COPY_FROM;
        // A phase after the default one, where fabric-api transfers data attachments; EntityData orders its
        // reset of those transferred values between the two.
        if (!playerCopyPhaseOrdered) {
            playerCopyPhaseOrdered = true;
            event.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE,
                    dev.golem.core.Ids.of(dev.golem.core.GolemCore.MOD_ID, "player_copy"));
        }
        event.register(dev.golem.core.Ids.of(dev.golem.core.GolemCore.MOD_ID, "player_copy"), handler::copy);
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) -> {
                    if (event.getOriginal() instanceof net.minecraft.server.level.ServerPlayer old
                            && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer copy) {
                        handler.copy(old, copy, !event.isWasDeath());
                    }
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerEvent.Clone event) -> {
                    if (event.getOriginal() instanceof net.minecraft.server.level.ServerPlayer old
                            && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer copy) {
                        handler.copy(old, copy, !event.isWasDeath());
                    }
                });
        *///?}
    }

    /**
     * A player has joined the server and stands in its level. {@code firstTime} is true when this player has never
     * played in this world before - vanilla found no saved player data, so its statistics are fresh (no play time, never
     * left the game); 1.7 mods kept a per-player flag for this (hbm-space's {@code HbmPlayerProps.hasWarped}).
     * NeoForge/Forge {@code PlayerLoggedInEvent}, Fabric {@code ServerPlayConnectionEvents.JOIN}.
     */
    public static final dev.golem.core.hook.Hook<PlayerJoin> PLAYER_JOIN = dev.golem.core.hook.Hook.notifying();
    private static boolean playerJoinHooked;

    /** Registers a {@link #PLAYER_JOIN} listener. */
    public static synchronized void onPlayerJoin(PlayerJoin handler) {
        PLAYER_JOIN.register(handler);
        if (playerJoinHooked) return;
        playerJoinHooked = true;
        //? if fabric {
        /*net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (listener, sender, server) -> playerJoined(listener.getPlayer()));
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
                    if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) playerJoined(player);
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
                    if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) playerJoined(player);
                });
        *///?}
    }

    /**
     * Whether {@code player} has never played in this world before: no play time and no "leave game" in its statistics,
     * which vanilla loads from the world's {@code stats} folder with the player (a new player starts both at 0).
     */
    public static boolean isFirstJoin(net.minecraft.server.level.ServerPlayer player) {
        var stats = player.getStats();
        return stats.getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME)) == 0
                && stats.getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.LEAVE_GAME)) == 0;
    }

    private static void playerJoined(net.minecraft.server.level.ServerPlayer player) {
        boolean first = isFirstJoin(player);
        for (PlayerJoin listener : PLAYER_JOIN) listener.joined(player, first);
    }

    @FunctionalInterface
    public interface PlayerJoin {
        void joined(net.minecraft.server.level.ServerPlayer player, boolean firstTime);
    }

    @FunctionalInterface
    public interface PlayerCopy {
        void copy(net.minecraft.server.level.ServerPlayer oldPlayer, net.minecraft.server.level.ServerPlayer newPlayer,
                  boolean alive);
    }

    /**
     * The server is sending its datapack contents (tags, recipes) to {@code players}: one player joining, or everyone
     * after a {@code /reload} (possibly nobody). A mod syncs its own data-driven tables to the same players here.
     */
    public static void onDatapackSync(BiConsumer<MinecraftServer, java.util.List<net.minecraft.server.level.ServerPlayer>> handler) {
        //? if fabric {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(
                (player, joined) -> handler.accept(((ServerLevel) player.level()).getServer(), java.util.List.of(player)));
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.OnDatapackSyncEvent event) -> handler.accept(
                        event.getPlayerList().getServer(), event.getRelevantPlayers().toList()));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.OnDatapackSyncEvent event) -> handler.accept(
                        event.getPlayerList().getServer(),
                        event.getPlayer() != null ? java.util.List.of(event.getPlayer()) : event.getPlayerList().getPlayers()));
        *///?}
    }

    /**
     * The server's datapack contents (recipes, tags, ...) are new and not yet sent to any player: once before the server
     * starts, and after every {@code /reload}, before the reload's sync packets. A handler may change the server's recipe
     * manager here and every player receives the changed set.
     *
     * <p>On Fabric the reload hook is per-player ({@code SYNC_DATA_PACK_CONTENTS}), so the handler can run once per
     * player, plus once more after the reload ({@code END_DATA_PACK_RELOAD}, for a reload with nobody online): it must be
     * idempotent, e.g. by remembering the recipe manager instance it last handled (each reload creates a new one).
     */
    public static void onDatapackReloaded(Consumer<MinecraftServer> handler) {
        //? if fabric {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(handler::accept);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(
                (player, joined) -> handler.accept(((ServerLevel) player.level()).getServer()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
                (server, resources, success) -> handler.accept(server));
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,
                (net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) -> handler.accept(event.getServer()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,
                (net.neoforged.neoforge.event.OnDatapackSyncEvent event) -> {
                    // A null player is the /reload broadcast; a joining player sees the set already handled.
                    if (event.getPlayer() == null) handler.accept(event.getPlayerList().getServer());
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,
                (net.minecraftforge.event.server.ServerAboutToStartEvent event) -> handler.accept(event.getServer()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,
                (net.minecraftforge.event.OnDatapackSyncEvent event) -> {
                    if (event.getPlayer() == null) handler.accept(event.getPlayerList().getServer());
                });
        *///?}
    }

    /** Runs as the server begins starting, before any level or block entity is loaded. */
    public static void onServerStarting(Consumer<MinecraftServer> handler) {
        //? if fabric {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(handler::accept);
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) -> handler.accept(event.getServer()));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerAboutToStartEvent event) -> handler.accept(event.getServer()));
        *///?}
    }

    /** Runs once the server has finished starting, every level loaded. */
    public static void onServerStarted(Consumer<MinecraftServer> handler) {
        //? if fabric {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(handler::accept);
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStartedEvent event) -> handler.accept(event.getServer()));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerStartedEvent event) -> handler.accept(event.getServer()));
        *///?}
    }

    /** Runs when the server begins stopping, before levels are saved and unloaded. */
    public static void onServerStopping(Consumer<MinecraftServer> handler) {
        //? if fabric {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(handler::accept);
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> handler.accept(event.getServer()));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerStoppingEvent event) -> handler.accept(event.getServer()));
        *///?}
    }
}
