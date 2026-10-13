package dev.golem.core.world;

import dev.golem.core.platform.Lifecycle;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * A chunk has become fully accessible, announced on the rising edge, and only there.
 *
 * <p>A loader's chunk-load event is not this: it fires once, when the chunk enters the level. A chunk
 * whose ticket level falls below {@code FULL} and recovers before it unloads is never announced again,
 * so per-chunk state built from a load event misses the re-promotion (walking to the edge of a network
 * and back is enough).
 *
 * <p>The edge is computed here because vanilla's argument does not carry it.
 * {@code ChunkMap.onFullChunkStatusChange} reports the new status on a promotion into that status and on
 * <em>any</em> demotion, so a chunk dropping from {@code BLOCK_TICKING} to {@code FULL} (whenever a
 * player crosses the simulation boundary) reports {@code FULL} exactly as a genuine promotion does.
 * Recording which chunks are up separates the two, independent of which edges a version reports.
 */
public final class ChunkPromotions {

    private static final List<BiConsumer<ServerLevel, LevelChunk>> HANDLERS = new ArrayList<>();
    private static final RisingEdges<ServerLevel> FULL = new RisingEdges<>();

    /**
     * Chunks whose edge has been recorded but whose chunk object was not yet obtainable, retried at the
     * end of each tick. Vanilla announces the promotion from a callback scheduled on the main thread,
     * and the chunk is not necessarily in the visible map when it runs, so the edge and the delivery
     * cannot be the same moment.
     */
    private static final Map<ServerLevel, LongArrayList> WAITING = new IdentityHashMap<>();

    private static boolean installed;

    private ChunkPromotions() {}

    /**
     * Runs when a chunk rises into full accessibility, whether it has just loaded or has been
     * re-promoted after falling below.
     */
    public static void onRise(BiConsumer<ServerLevel, LevelChunk> handler) {
        if (!installed) {
            installed = true;
            Lifecycle.onLevelUnload((server, level) -> {
                FULL.forget(level);
                WAITING.remove(level);
            });
            Lifecycle.onServerTick(server -> drain());
        }
        HANDLERS.add(handler);
    }

    /**
     * The mixin's entry point, called for every full-status change vanilla reports. Public because
     * the mixin package can see nothing narrower; not something a consumer should call.
     */
    public static void statusChanged(ServerLevel level, ChunkPos pos, FullChunkStatus status) {
        long key = Chunks.key(pos);
        if (!status.isOrAfter(FullChunkStatus.FULL)) {
            FULL.fall(level, key);
            return;
        }
        if (!FULL.rise(level, key)) return;
        if (!announce(level, key)) {
            WAITING.computeIfAbsent(level, l -> new LongArrayList()).add(key);
        }
    }

    /** Delivers one chunk to the handlers, or answers false if the chunk is not obtainable yet. */
    private static boolean announce(ServerLevel level, long key) {
        ChunkPos pos = Chunks.unkey(key);
        LevelChunk chunk = level.getChunkSource().getChunkNow(Chunks.chunkX(pos), Chunks.chunkZ(pos));
        if (chunk == null) return false;
        for (int i = 0; i < HANDLERS.size(); i++) HANDLERS.get(i).accept(level, chunk);
        return true;
    }

    /**
     * Retries what the promotion callback could not deliver. A chunk that has fallen out of full
     * accessibility in the meantime is dropped rather than announced late.
     */
    private static void drain() {
        if (WAITING.isEmpty()) return;
        for (Map.Entry<ServerLevel, LongArrayList> entry : WAITING.entrySet()) {
            ServerLevel level = entry.getKey();
            LongArrayList waiting = entry.getValue();
            LongArrayList retry = null;
            for (LongIterator it = waiting.iterator(); it.hasNext(); ) {
                long key = it.nextLong();
                if (!FULL.isUp(level, key)) continue;
                if (announce(level, key)) continue;
                if (retry == null) retry = new LongArrayList();
                retry.add(key);
            }
            waiting.clear();
            if (retry != null) waiting.addAll(retry);
        }
        WAITING.values().removeIf(LongArrayList::isEmpty);
    }

    /**
     * Whether this chunk is currently recorded as full. Only the mixin writes that record, so a test
     * asking this is asking whether the hook reached it at all.
     */
    public static boolean isUp(ServerLevel level, ChunkPos pos) {
        return FULL.isUp(level, Chunks.key(pos));
    }

    /** Chunks currently recorded as full, across every level. For a test asserting this is bounded. */
    public static int upCount() {
        return FULL.size();
    }
}
