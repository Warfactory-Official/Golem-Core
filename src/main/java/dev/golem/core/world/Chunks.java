package dev.golem.core.world;

import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;

import dev.golem.core.GolemCore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Chunk access whose shape changed across the supported range. */
public final class Chunks {

    private Chunks() {}

    /** A full chunk already in memory, without requesting loading or generation. */
    public static @Nullable LevelChunk loaded(Level level, int chunkX, int chunkZ) {
        if (level instanceof ServerLevel server) return server.getChunkSource().getChunkNow(chunkX, chunkZ);
        //? if >=1.21 {
        return (LevelChunk) level.getChunk(chunkX, chunkZ,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
        //?} else {
        /*return (LevelChunk) level.getChunk(chunkX, chunkZ,
                net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
        *///?}
    }
    /** The lowest buildable y of {@code level} (1.21.2 {@code getMinY}; earlier {@code getMinBuildHeight}). */
    public static int minY(LevelReader level) {
        //? if >=1.21.2 {
        return level.getMinY();
        //?} else {
        /*return level.getMinBuildHeight();
        *///?}
    }

    /**
     * Ownership that vanilla's set-like ticket collection does not retain. Multiple bulk operations may
     * claim the same chunk; only the final release may remove their shared vanilla ticket.
     */
    private static final Map<ServerLevel, Map<Long, Integer>> LOAD_REFS = new WeakHashMap<>();

    /**
     * A ticket that <b>loads a chunk without simulating it</b>.
     *
     * <p>Vanilla's {@code FORCED} ticket (what {@code ServerLevel.setChunkForced} uses) grants loading <em>and</em>
     * simulation, so every chunk held ticks: mob spawning, random ticks, block ticks, entity ticks. Bulk world work
     * such as a fallout sweep over hundreds of generated chunks only reads and writes blocks.
     *
     * <p>{@code ntm-next} registers {@code BLAST_LOAD} with {@code FLAG_LOADING} and without
     * {@code FLAG_SIMULATION}; {@code 1.12.2} uses a Forge loading ticket.
     *
     * <p>26.1 made {@code TicketType} a record of {@code (timeout, flags)}. Before that it is a named type and the
     * <em>level</em> carries the meaning: 33 is "full chunk, not ticking" and {@code addRegionTicket} computes
     * {@code 33 - radius}. Neither form is persisted (no {@code FLAG_PERSIST}, no {@code forceLoad} flag), so a
     * claim that outlives a crash disappears, which is the right failure mode for one.
     */
    //? if >=26.1 {
    public static final TicketType LOAD_ONLY =
            new net.minecraft.server.level.TicketType(
                    net.minecraft.server.level.TicketType.NO_TIMEOUT,
                    net.minecraft.server.level.TicketType.FLAG_LOADING);
    //?} else {
    /*public static final TicketType<ChunkPos> LOAD_ONLY =
            net.minecraft.server.level.TicketType.create("golem_load_only",
                    java.util.Comparator.comparingLong(ChunkPos::toLong));
    *///?}

    /**
     * Holds a chunk in memory, loading or generating it in the background, <b>without making it tick</b>.
     *
     * <p>Returns immediately. The chunk shows up in {@code getChunkNow} once the chunk system has
     * produced it; callers poll for that rather than waiting. Balanced by {@link #releaseForLoading}.
     */
    public static synchronized void claimForLoading(ServerLevel level, int chunkX, int chunkZ) {
        long key = pack(chunkX, chunkZ);
        Map<Long, Integer> refs = LOAD_REFS.computeIfAbsent(level, ignored -> new HashMap<>());
        Integer previous = refs.put(key, refs.getOrDefault(key, 0) + 1);
        if (previous != null) return;

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        //? if >=26.1 {
        level.getChunkSource().addTicketWithRadius(LOAD_ONLY, pos, 0);
        //?} else {
        /*level.getChunkSource().addRegionTicket(LOAD_ONLY, pos, 0, pos);
        *///?}
    }

    /**
     * Loads and simulates a chunk under a ticket of its own. Vanilla's shared {@code FORCED} ticket is one entry per
     * chunk, so a holder that removes it takes another holder's forced chunk out of entity ticking.
     */
    //? if >=26.1 {
    private static final TicketType TICKING = new TicketType(TicketType.NO_TIMEOUT,
            TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    //?} else {
    /*private static final TicketType<ChunkPos> TICKING =
            TicketType.create("golem_ticking", Comparator.comparingLong(ChunkPos::toLong));
    *///?}

    /** Radius 2 is ticket level 31 (entity-ticking), the level of a forced chunk. */
    private static final int TICKING_RADIUS = 2;

    /**
     * Holds a chunk entity-ticking until {@link #releaseTicking}, without waiting for it to load (unlike
     * {@code setChunkForced}). Not counted: the caller counts its own holders.
     */
    public static void claimTicking(ServerLevel level, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        //? if >=26.1 {
        level.getChunkSource().addTicketWithRadius(TICKING, pos, TICKING_RADIUS);
        //?} else {
        /*level.getChunkSource().addRegionTicket(TICKING, pos, TICKING_RADIUS, pos);
        *///?}
    }

    /** Drops the claim taken by {@link #claimTicking}. */
    public static void releaseTicking(ServerLevel level, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        //? if >=26.1 {
        level.getChunkSource().removeTicketWithRadius(TICKING, pos, TICKING_RADIUS);
        //?} else {
        /*level.getChunkSource().removeRegionTicket(TICKING, pos, TICKING_RADIUS, pos);
        *///?}
    }
    /** Waits until a caller-held chunk ticks entities and has read its stored ones; takes and releases no claim. */
    public static void loadEntityTicking(ServerLevel level, int chunkX, int chunkZ) {
        var cache = level.getChunkSource();
        // A FULL chunk can already be cached while its newly claimed entity-ticking future is still unloaded.
        cache.runDistanceManagerUpdates();
        level.getChunk(chunkX, chunkZ);
        var pos = new ChunkPos(chunkX, chunkZ);
        var ticking = cache.getVisibleChunkIfPresent(pack(chunkX, chunkZ)).getEntityTickingChunkFuture();
        level.getServer().managedBlock(() -> {
            if (ticking.isDone()) return true;
            // The server's ordinary chunk polling is budgeted by its tick deadline; this blocking wait is not.
            cache.pollTask();
            return ticking.isDone();
        });
        var result = ticking.join();
        //? if >=1.21.1 {
        result.orElseThrow(() -> new IllegalStateException("Entity-ticking chunk " + pos + ": " + result.getError()));
        //?} else {
        /*result.left().orElseThrow(() -> new IllegalStateException(
                "Entity-ticking chunk " + pos + ": " + result.right().orElseThrow()));
        *///?}
        //? if >=26.1 {
        level.waitForEntities(pos, 0);
        //?} else {
        /*level.getServer().managedBlock(() -> {
            // the chunk's status change that requests its entities runs on the chunk executor, not the server's
            cache.pollTask();
            level.entityManager.processPendingLoads();
            return level.areEntitiesLoaded(pack(chunkX, chunkZ));
        });
        *///?}
    }

    /** Waits for a caller-held chunk's native send dependencies, including lighting, before a player send. */
    public static LevelChunk loadForSending(ServerLevel level, int chunkX, int chunkZ) {
        loadEntityTicking(level, chunkX, chunkZ);
        var cache = level.getChunkSource();
        var holder = cache.getVisibleChunkIfPresent(pack(chunkX, chunkZ));
        //? if >=1.21.1 {
        level.getServer().managedBlock(() -> {
            var sending = holder.getSendSyncFuture();
            if (sending.isDone()) {
                sending.join();
                if (holder.getChunkToSend() != null) return true;
            }
            cache.pollTask();
            return false;
        });
        return holder.getChunkToSend();
        //?} else {
        /*return holder.getTickingChunk();
        *///?}
    }

    /**
     * Loads or generates a chunk on the calling server thread and returns it, blocking until it is a full
     * chunk. The synchronous fallback for a claim the chunk system does not answer.
     */
    public static LevelChunk loadNow(ServerLevel level, int chunkX, int chunkZ) {
        //? if >=1.21 {
        net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunkSource().getChunk(chunkX, chunkZ,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
        //?} else {
        /*net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunkSource().getChunk(chunkX, chunkZ,
                net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
        *///?}
        return chunk instanceof LevelChunk full ? full : null;
    }

    /** Drops a claim taken by {@link #claimForLoading}. */
    public static synchronized void releaseForLoading(ServerLevel level, int chunkX, int chunkZ) {
        long key = pack(chunkX, chunkZ);
        Map<Long, Integer> refs = LOAD_REFS.get(level);
        Integer count = refs == null ? null : refs.get(key);
        if (count == null) {
            GolemCore.LOG.warn("Ignoring unmatched loading-claim release for chunk [{}, {}]", chunkX, chunkZ);
            return;
        }
        if (count > 1) {
            refs.put(key, count - 1);
            return;
        }
        refs.remove(key);
        if (refs.isEmpty()) LOAD_REFS.remove(level);

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        //? if >=26.1 {
        level.getChunkSource().removeTicketWithRadius(LOAD_ONLY, pos, 0);
        //?} else {
        /*level.getChunkSource().removeRegionTicket(LOAD_ONLY, pos, 0, pos);
        *///?}
    }

    /**
     * A chunk's identity as one long, in Golem's own layout rather than vanilla's.
     *
     * <p>Vanilla's spelling differs by version ({@code ChunkPos.asLong}/{@code toLong} before 26.1,
     * {@code ChunkPos.pack}/{@code pack()} from it), so a key derived from it would need a version branch at every
     * site. This packing has no version, which is worth more than matching vanilla's bit order for a map key.
     */
    public static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    /** The chunk containing a block position, as a {@link #key(int, int)}. */
    public static long keyOfBlock(int blockX, int blockZ) {
        return key(net.minecraft.core.SectionPos.blockToSectionCoord(blockX),
                net.minecraft.core.SectionPos.blockToSectionCoord(blockZ));
    }

    /**
     * 26.1 made {@code ChunkPos.x}/{@code z} private behind accessors that older versions do not have,
     * so neither spelling of the field works everywhere. The block bounds are public on all of them.
     */
    public static long key(ChunkPos pos) {
        return keyOfBlock(pos.getMinBlockX(), pos.getMinBlockZ());
    }

    /** A chunk's x, read through the bounds rather than the field {@link #key(ChunkPos)} avoids. */
    public static int chunkX(ChunkPos pos) {
        return net.minecraft.core.SectionPos.blockToSectionCoord(pos.getMinBlockX());
    }

    /** A chunk's z, read the same way as {@link #chunkX(ChunkPos)}. */
    public static int chunkZ(ChunkPos pos) {
        return net.minecraft.core.SectionPos.blockToSectionCoord(pos.getMinBlockZ());
    }

    public static ChunkPos unkey(long key) {
        return new ChunkPos((int) key, (int) (key >> 32));
    }

    private static long pack(int chunkX, int chunkZ) {
        return key(chunkX, chunkZ);
    }

    /**
     * The saved (on-disk) height map of a chunk that is not loaded, read without loading or generating it: 1.7's
     * {@code provideChunk} off the region file, minus the chunk. Completes with 256 absolute heights (index
     * {@code x + z * 16}, the {@code heightmap} type's first free y, as {@code Level.getHeight} answers it), or
     * with null when the chunk was never saved, is not a finished chunk, or carries no such height map. The
     * read runs on the chunk map's IO worker; completion may come on any thread.
     */
    public static java.util.concurrent.CompletableFuture<int @org.jspecify.annotations.Nullable []> readSavedHeights(
            ServerLevel level, int chunkX, int chunkZ, net.minecraft.world.level.levelgen.Heightmap.Types heightmap) {
        //? if >=1.21.2 {
        int minY = level.getMinY();
        //?} else {
        /*int minY = level.getMinBuildHeight();
        *///?}
        int height = level.getHeight();
        return level.getChunkSource().chunkMap.read(new ChunkPos(chunkX, chunkZ)).handle((stored, error) -> {
            if (error != null || stored == null || stored.isEmpty()) return null;
            return savedHeights(stored.get(), heightmap.getSerializationKey(), minY, height);
        });
    }

    private static int @org.jspecify.annotations.Nullable [] savedHeights(net.minecraft.nbt.CompoundTag chunk, String key,
                                                                          int minY, int height) {
        //? if >=26.1 {
        String status = chunk.getStringOr("Status", "");
        net.minecraft.nbt.CompoundTag maps = chunk.getCompoundOrEmpty("Heightmaps");
        long[] data = maps.getLongArray(key).orElse(null);
        //?} else {
        /*String status = chunk.getString("Status");
        net.minecraft.nbt.CompoundTag maps = chunk.getCompound("Heightmaps");
        long[] data = maps.contains(key, 12) ? maps.getLongArray(key) : null;
        *///?}
        if (!status.equals("minecraft:full") && !status.equals("full")) return null;
        if (data == null) return null;
        int bits = net.minecraft.util.Mth.ceillog2(height + 1);
        try {
            net.minecraft.util.SimpleBitStorage storage = new net.minecraft.util.SimpleBitStorage(bits, 256, data);
            int[] out = new int[256];
            for (int i = 0; i < 256; i++) out[i] = storage.get(i) + minY;
            return out;
        } catch (RuntimeException badLength) {
            return null;
        }
    }
}
