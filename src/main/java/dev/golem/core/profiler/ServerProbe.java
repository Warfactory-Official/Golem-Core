package dev.golem.core.profiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.golem.core.Ids;
import dev.golem.core.platform.Lifecycle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * Server tick times (from the start of level ticking to the end of the tick, the part a mod can slow down) and a
 * census of entities and block entities near players at the end of the capture.
 */
final class ServerProbe implements Profiler.Probe {

    private static @Nullable ServerProbe active;
    private static boolean hooked;

    private final MinecraftServer server;
    private final @Nullable ServerPlayer viewer;
    private final TimeSeries ticks = new TimeSeries();
    private long tickStart;
    private int shown = -1;

    /** {@code viewer}, when set, sees the countdown in their action bar. */
    ServerProbe(MinecraftServer server, @Nullable ServerPlayer viewer) {
        this.server = server;
        this.viewer = viewer;
    }

    /** Installs the tick hooks once; they cost a field read while no capture runs. */
    static void hook() {
        if (hooked) return;
        hooked = true;
        Lifecycle.onBeforeLevelTicks(server -> {
            ServerProbe probe = active;
            if (probe != null && probe.server == server) probe.tickStart = System.nanoTime();
        });
        Lifecycle.onServerTick(server -> {
            ServerProbe probe = active;
            if (probe == null || probe.server != server || probe.tickStart == 0) return;
            long now = System.nanoTime();
            probe.ticks.add(Profiler.seconds(now), (now - probe.tickStart) / 1e6);
            probe.showCountdown();
        });
    }

    private void showCountdown() {
        int left = Profiler.secondsLeft();
        if (viewer == null || left == shown || viewer.hasDisconnected()) return;
        shown = left;
        viewer.connection.send(new ClientboundSetActionBarTextPacket(ProfilerCommands.countdown(left)));
    }

    @Override
    public String key() {
        return "server";
    }

    @Override
    public void start() {
        hook();
        active = this;
    }

    @Override
    public void stop() {
        if (active == this) active = null;
    }

    @Override
    public CompletableFuture<JsonElement> collect() {
        return server.submit(this::census).thenApply(census -> {
            census.add("ticks", ticks.toJson());
            return census;
        });
    }

    @Override
    public Profiler.Spikes spikes() {
        return new Profiler.Spikes("Server thread", "ticks over 50 ms", 50, ticks.windowsOver(50));
    }

    private JsonObject census() {
        JsonObject json = new JsonObject();
        json.addProperty("dedicated", server.isDedicatedServer());
        json.addProperty("players", server.getPlayerList().getPlayerCount());
        json.addProperty("viewDistance", server.getPlayerList().getViewDistance());
        JsonArray levels = new JsonArray();
        for (ServerLevel level : server.getAllLevels()) levels.add(levelCensus(level));
        json.add("levels", levels);
        return json;
    }

    private JsonObject levelCensus(ServerLevel level) {
        JsonObject json = new JsonObject();
        json.addProperty("name", String.valueOf(Ids.keyId(level.dimension())));
        json.addProperty("loadedChunks", level.getChunkSource().getLoadedChunksCount());
        Map<String, Integer> entities = new HashMap<>();
        for (Entity entity : level.getAllEntities()) {
            entities.merge(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())), 1, Integer::sum);
        }
        json.add("entities", Census.sorted(entities));
        Map<String, Integer> blockEntities = new HashMap<>();
        int radius = server.getPlayerList().getViewDistance();
        Set<Long> visited = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            int cx = player.blockPosition().getX() >> 4;
            int cz = player.blockPosition().getZ() >> 4;
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if (!visited.add(((long) x << 32) ^ (z & 0xffffffffL))) continue;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        blockEntities.merge(String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType())),
                                1, Integer::sum);
                    }
                }
            }
        }
        json.addProperty("chunksAroundPlayers", visited.size());
        json.add("blockEntitiesAroundPlayers", Census.sorted(blockEntities));
        return json;
    }
}
