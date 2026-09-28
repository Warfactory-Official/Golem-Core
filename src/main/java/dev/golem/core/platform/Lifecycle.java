package dev.golem.core.platform;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Server lifecycle callbacks, under one set of names on every loader and every version.
 *
 * <p>These exist because fabric-api renamed several of its own events between the 1.21.1 and 26.1
 * lines — {@code ServerWorldEvents} became {@code ServerLevelEvents}, and {@code CHUNK_LOAD} grew a
 * third "newly generated" parameter, changing its SAM. None of that is a Minecraft difference, so none
 * of it should be visible to a consumer: a mod that wants to know when a level unloads should say so
 * once.
 *
 * <p>NeoForge and Forge are kept apart rather than folded into {@code forgelike}: the whole event
 * package moved from {@code net.minecraftforge} to {@code net.neoforged}, and the tick event was
 * re-modelled from one class with a phase field into separate Pre/Post classes. That is two different
 * APIs wearing similar names, not one API with a rename.
 *
 * <p>Registration timing is unchanged — each loader's entrypoint still calls these at the moment it
 * always did. This only removes the naming churn, not the ordering.
 */
public final class Lifecycle {

    private Lifecycle() {}

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
    /**
     * Runs when a server player is replaced by a fresh copy - respawn after death ({@code alive} false) or the
     * return from the End ({@code alive} true) - with the old and the new player. Forge/NeoForge
     * {@code PlayerEvent.Clone}, Fabric {@code ServerPlayerEvents.COPY_FROM}: state a mod keeps on the player
     * object itself is lost unless it is copied here.
     */
    public static void onPlayerCopy(PlayerCopy handler) {
        //? if fabric {
        /*net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.COPY_FROM.register(handler::copy);
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

    @FunctionalInterface
    public interface PlayerCopy {
        void copy(net.minecraft.server.level.ServerPlayer oldPlayer, net.minecraft.server.level.ServerPlayer newPlayer,
                  boolean alive);
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
