package dev.golem.core.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Chunk access whose shape changed across the supported range. */
public final class Chunks {

    private Chunks() {}

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
    public static void claimForLoading(ServerLevel level, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        //? if >=26.1 {
        level.getChunkSource().addTicketWithRadius(LOAD_ONLY, pos, 0);
        //?} else {
        /*level.getChunkSource().addRegionTicket(LOAD_ONLY, pos, 0, pos);
        *///?}
    }

    /** Drops a claim taken by {@link #claimForLoading}. */
    public static void releaseForLoading(ServerLevel level, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        //? if >=26.1 {
        level.getChunkSource().removeTicketWithRadius(LOAD_ONLY, pos, 0);
        //?} else {
        /*level.getChunkSource().removeRegionTicket(LOAD_ONLY, pos, 0, pos);
        *///?}
    }
}
