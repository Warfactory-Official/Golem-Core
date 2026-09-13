package dev.golem.core.world;

import dev.golem.core.GolemCore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Chunk access whose shape changed across the supported range. */
public final class Chunks {

    private Chunks() {}

    /**
     * Ownership that vanilla's set-like ticket collection does not retain. Multiple bulk operations may
     * claim the same chunk; only the final release may remove their shared vanilla ticket.
     */
    private static final Map<ServerLevel, Map<Long, Integer>> LOAD_REFS = new WeakHashMap<>();

    /**
     * A ticket that <b>loads a chunk without simulating it</b>.
     *
     * <p>This is the distinction that matters for bulk world work. Vanilla's {@code FORCED} ticket —
     * what {@code ServerLevel.setChunkForced} uses — grants loading <em>and</em> simulation, so every
     * chunk held starts ticking: mob spawning, random ticks, block ticks, entity ticks. A fallout sweep
     * holding three hundred freshly generated chunks was therefore running tens of thousands of random
     * ticks a tick for terrain nobody was standing in. It only ever wanted to read and write blocks.
     *
     * <p>Both references use a loading-only ticket for exactly this: {@code ntm-next} registers
     * {@code BLAST_LOAD} with {@code FLAG_LOADING} and deliberately without {@code FLAG_SIMULATION},
     * and {@code old-hbm} uses a Forge loading ticket.
     *
     * <p>The two eras express it differently and both are spelled out below. 26.1 made {@code TicketType}
     * a record of {@code (timeout, flags)}; before that it is a named type and the <em>level</em> carries
     * the meaning, where 33 is "full chunk, not ticking" and {@code addRegionTicket} computes
     * {@code 33 - radius}. Neither is persisted — no {@code FLAG_PERSIST}, no {@code forceLoad} flag — so
     * a claim that outlives a crash simply disappears, which is the right failure mode for one.
     */
    //? if >=26.1 {
    private static final net.minecraft.server.level.TicketType LOAD_ONLY =
            new net.minecraft.server.level.TicketType(
                    net.minecraft.server.level.TicketType.NO_TIMEOUT,
                    net.minecraft.server.level.TicketType.FLAG_LOADING);
    //?} else {
    /*private static final net.minecraft.server.level.TicketType<ChunkPos> LOAD_ONLY =
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
     * <p>Vanilla's spelling moved — {@code ChunkPos.asLong}/{@code toLong} became
     * {@code ChunkPos.pack}/{@code pack()} at 26.1 — so a key derived from it would need a version
     * branch at every site that builds one. This packing is ours and therefore has no version, which
     * is worth more than matching vanilla's bit order for a value that is only ever a map key.
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
}
