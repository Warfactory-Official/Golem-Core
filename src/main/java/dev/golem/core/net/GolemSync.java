package dev.golem.core.net;

import dev.golem.core.platform.Lifecycle;
import dev.golem.core.world.Chunks;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Replication for per-position state that a block entity pushes to whoever is watching it.
 *
 * <p>A sender asks {@link #isWatched} before it does any work, and {@link #publish}es only when the
 * state it would send has actually changed. The table keeps the last wire published at each position
 * and replays it to players who arrive afterwards, so "publish only on change" does not strand a
 * player who was not connected when the change happened. Neither half is safe alone: the gate without
 * the table silently desynchronises late arrivals, and the table without the gate keeps paying to
 * serialise state nobody is looking at.
 *
 * <p><b>Deviations from the system this is modelled on.</b> Scope is the chunk-tracking set rather
 * than a radius, because that is what the underlying transport already targets and it needs no second
 * distance rule. Departed players are dropped by reconciling each entry against the live tracking list
 * rather than by a logout callback, which removes the accumulation an unhooked disconnect would
 * otherwise leave behind. Tracking lists are resolved once per chunk per tick and shared by every
 * entry in that chunk.
 *
 * <p>A position whose state changes while nobody watches keeps its last published wire until its next
 * publish. A player arriving in that window is healed with the older wire first and corrected on the
 * sender's next tick.
 */
public final class GolemSync {

    private record Published(GolemCodec<?> codec, GolemPayload payload) {}

    private static final class Entry {
        private Published published;
        private final ObjectOpenHashSet<UUID> sent = new ObjectOpenHashSet<>(2);
    }

    private static final Map<ServerLevel, Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<Entry>>> TABLE =
            new IdentityHashMap<>();
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<List<ServerPlayer>>> TRACKING =
            new IdentityHashMap<>();

    private static boolean installed;

    /**
     * Watcher sets stated by a test, per level and chunk, consulted ahead of live chunk tracking.
     *
     * <p>Production never writes to this. It exists because a headless test server cannot produce
     * live chunk tracking — a mock player is in the level but never passes through the chunk map, so
     * {@code getPlayers} never names it — and the replication logic above the lookup is where the
     * subtle failure modes are. Keying by chunk rather than replacing the lookup wholesale keeps
     * concurrently running tests from overwriting one another's answer.
     */
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<List<ServerPlayer>>> STATED =
            new IdentityHashMap<>();

    private GolemSync() {}

    /** States who watches the chunk containing {@code pos}, for a test. */
    public static void stateWatchers(ServerLevel level, BlockPos pos, List<ServerPlayer> players) {
        STATED.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>())
                .put(chunkKey(pos), List.copyOf(players));
        TRACKING.remove(level);
    }

    /** Drops a statement made by {@link #stateWatchers}. */
    public static void clearStatedWatchers(ServerLevel level, BlockPos pos) {
        Long2ObjectOpenHashMap<List<ServerPlayer>> stated = STATED.get(level);
        if (stated == null) return;
        stated.remove(chunkKey(pos));
        if (stated.isEmpty()) STATED.remove(level);
        TRACKING.remove(level);
    }

    /** Whether a watcher set has been stated for the chunk containing {@code pos}. */
    public static boolean hasStatedWatchers(ServerLevel level, BlockPos pos) {
        Long2ObjectOpenHashMap<List<ServerPlayer>> stated = STATED.get(level);
        return stated != null && stated.containsKey(chunkKey(pos));
    }

    /**
     * Attaches the tick pump and the eviction hooks. Safe to call from more than one entrypoint; only
     * the first call registers anything.
     */
    public static void install() {
        if (installed) return;
        installed = true;
        dev.golem.core.GolemCore.LOG.info("Payload transport: requested {}, channel accessors {}",
                GolemTransport.requested(),
                GolemWire.mixinsApplied() ? "present" : "ABSENT (will use the safe transport)");
        Lifecycle.onServerTick(server -> {
            TRACKING.clear();
            pump();
            GolemTransport.endTick();
        });
        Lifecycle.onChunkUnload(GolemSync::evictChunk);
        Lifecycle.onLevelUnload((server, level) -> {
            TABLE.remove(level);
            TRACKING.remove(level);
            STATED.remove(level);
        });
    }

    /** Whether any player's chunk-tracking set holds this position. One lookup per chunk per tick. */
    public static boolean isWatched(ServerLevel level, BlockPos pos) {
        return !tracking(level, chunkKey(pos)).isEmpty();
    }

    /**
     * Records this position's current wire and sends it to everyone tracking the position now. The
     * payload is retained until the next publish, so it must not be mutated after this call.
     */
    public static <P extends GolemPayload> void publish(ServerLevel level, BlockPos pos,
            GolemCodec<P> codec, P payload) {
        long chunk = chunkKey(pos);
        Entry entry = TABLE.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(chunk, c -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(pos.asLong(), p -> new Entry());
        entry.published = new Published(codec, payload);
        entry.sent.clear();
        heal(level, entry, tracking(level, chunk), false);
    }

    /** Drops this position's entry. Call when the block entity is removed. */
    public static void retract(ServerLevel level, BlockPos pos) {
        Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<Entry>> chunks = TABLE.get(level);
        if (chunks == null) return;
        long chunk = chunkKey(pos);
        Long2ObjectOpenHashMap<Entry> positions = chunks.get(chunk);
        if (positions == null) return;
        positions.remove(pos.asLong());
        if (positions.isEmpty()) chunks.remove(chunk);
        if (chunks.isEmpty()) TABLE.remove(level);
    }

    /** Forgets every entry on every level. For a server stopping, and for tests between cases. */
    public static void clear() {
        TABLE.clear();
        TRACKING.clear();
        STATED.clear();
    }

    /** Whether a wire is currently held for this position. */
    public static boolean isPublished(ServerLevel level, BlockPos pos) {
        return entryAt(level, pos) != null;
    }

    /** How many players this position's entry believes it has already sent to. */
    public static int markedCount(ServerLevel level, BlockPos pos) {
        Entry entry = entryAt(level, pos);
        return entry == null ? 0 : entry.sent.size();
    }

    private static Entry entryAt(ServerLevel level, BlockPos pos) {
        Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<Entry>> chunks = TABLE.get(level);
        if (chunks == null) return null;
        Long2ObjectOpenHashMap<Entry> positions = chunks.get(chunkKey(pos));
        return positions == null ? null : positions.get(pos.asLong());
    }

    /** Runs the heal pass and the batched flush now instead of waiting for the end of the tick. */
    public static void pumpNow() {
        pumpHealOnly();
        GolemTransport.endTick();
    }

    /** The heal pass without the flush, so a test can see what batching left queued. */
    public static void pumpHealOnly() {
        TRACKING.clear();
        pump();
    }

    /** Entries currently held, across all levels. Exposed so a test can assert the table is bounded. */
    public static int size() {
        int total = 0;
        for (Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<Entry>> chunks : TABLE.values()) {
            for (Long2ObjectOpenHashMap<Entry> positions : chunks.values()) total += positions.size();
        }
        return total;
    }

    private static void evictChunk(ServerLevel level, LevelChunk chunk) {
        Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<Entry>> chunks = TABLE.get(level);
        if (chunks == null) return;
        chunks.remove(Chunks.key(chunk.getPos()));
        if (chunks.isEmpty()) TABLE.remove(level);
    }

    private static void pump() {
        if (TABLE.isEmpty()) return;
        for (Map.Entry<ServerLevel, Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<Entry>>> level : TABLE.entrySet()) {
            if (level.getKey().players().isEmpty()) continue;
            for (Long2ObjectMap.Entry<Long2ObjectOpenHashMap<Entry>> chunk
                    : level.getValue().long2ObjectEntrySet()) {
                List<ServerPlayer> watching = tracking(level.getKey(), chunk.getLongKey());
                for (Entry entry : chunk.getValue().values()) {
                    if (watching.isEmpty()) {
                        entry.sent.clear();
                    } else if (!inStep(entry, watching)) {
                        reconcile(entry, watching);
                        heal(level.getKey(), entry, watching, true);
                    }
                }
            }
        }
    }

    private static boolean inStep(Entry entry, List<ServerPlayer> watching) {
        if (entry.sent.size() != watching.size()) return false;
        for (ServerPlayer player : watching) {
            if (!entry.sent.contains(player.getUUID())) return false;
        }
        return true;
    }

    private static void reconcile(Entry entry, List<ServerPlayer> watching) {
        if (entry.sent.isEmpty()) return;
        entry.sent.removeIf(marked -> {
            for (ServerPlayer player : watching) {
                if (player.getUUID().equals(marked)) return false;
            }
            return true;
        });
    }

    /**
     * Sends this entry's wire to every watcher not already holding it. A state change goes out at
     * once; a replay to someone who has just come into view may wait for the end of the tick, which
     * is what lets one player's arrival cost one wake-up rather than one per sender.
     */
    private static void heal(ServerLevel level, Entry entry, List<ServerPlayer> watching, boolean batched) {
        Published published = entry.published;
        if (published == null) return;
        List<ServerPlayer> fresh = null;
        for (ServerPlayer player : watching) {
            if (!entry.sent.add(player.getUUID())) continue;
            if (fresh == null) fresh = new ArrayList<>(2);
            fresh.add(player);
        }
        if (fresh != null) send(level, published, fresh, batched);
    }

    @SuppressWarnings("unchecked")
    private static <P extends GolemPayload> void send(ServerLevel level, Published published,
            List<ServerPlayer> recipients, boolean batched) {
        GolemTransport.send(recipients, (GolemCodec<P>) published.codec(), (P) published.payload(),
                level, batched);
    }

    private static List<ServerPlayer> tracking(ServerLevel level, long chunk) {
        Long2ObjectOpenHashMap<List<ServerPlayer>> perChunk =
                TRACKING.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>());
        List<ServerPlayer> cached = perChunk.get(chunk);
        if (cached != null) return cached;
        List<ServerPlayer> resolved = trackedPlayers(level, chunk);
        perChunk.put(chunk, resolved);
        return resolved;
    }

    // The list handed back by ChunkMap is a view over live tracking state; a tick-long memo must not
    // alias it.
    private static List<ServerPlayer> trackedPlayers(ServerLevel level, long chunk) {
        Long2ObjectOpenHashMap<List<ServerPlayer>> stated = STATED.get(level);
        if (stated != null) {
            List<ServerPlayer> override = stated.get(chunk);
            if (override != null) return override;
        }
        if (level.players().isEmpty()) return List.of();
        List<ServerPlayer> tracking = level.getChunkSource().chunkMap.getPlayers(Chunks.unkey(chunk), false);
        return tracking.isEmpty() ? List.of() : new ArrayList<>(tracking);
    }

    private static long chunkKey(BlockPos pos) {
        return Chunks.keyOfBlock(pos.getX(), pos.getZ());
    }
}
