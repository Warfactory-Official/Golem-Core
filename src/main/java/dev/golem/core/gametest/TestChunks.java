package dev.golem.core.gametest;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
//? if >=26.1 {
import dev.golem.core.GolemCore;
import dev.golem.core.registry.DeferredRegister;
import dev.golem.core.registry.RegistryObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
//?} else {
/*import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
*///?}

/**
 * Keeps GameTest plots entity-ticking: the plot of every 26.1 test before it starts, and any chunks a test asks for
 * ({@link GameTests#holdChunks}).
 *
 * <p>26.1 starts a test once the chunks of its <em>structure</em> are entity-ticking ({@code GameTestInfo.tick}) and
 * forces only those ({@code TestInstanceBlockEntity.forceLoadChunks}). Every Golem test uses the 1x1x1
 * {@code minecraft:empty} template, so that is the one chunk holding the test's origin. A position a block or more away
 * can lie in the next chunk, which may be loaded but not entity-ticking: an entity there is not returned by
 * {@code getEntitiesOfClass} and never ticks, and a block entity there does not tick. Whether that happened depended on
 * where the plot fell in its chunk (a new position every run) and on what ran beside it, so such tests passed or failed
 * at random (debris that "ticks 0", a rocket "still LANDING", bullets not fired, a pig the reactor did not see). Below
 * 26.1 the padded empty templates cover the room and the structure chunks are the whole plot, so the hold is 26.1-only.
 *
 * <p>Before the first tick of a test declared through {@link GameTests} this takes a {@code golemcore:game_test} chunk
 * ticket (entity-ticking level, the same as a forced chunk's) on every chunk under the test's structure box inflated by
 * its declared padding, at least {@link #MARGIN} blocks, and generates those chunks and the ring of two around them on
 * the server thread (a chunk is entity-ticking once every chunk within two of it is FULL; left to the worker threads,
 * that took a machine- and batch-dependent number of ticks). The test does not tick (its tick count does not advance,
 * exactly as vanilla's own structure-chunk wait) until every one of those chunks is entity-ticking; that is normally a
 * tick or two of ticket promotion. The tickets are counted per chunk, because neighbouring plots share chunks, and are
 * released when the test passes or fails. They are not forced chunks, so the runner's end-of-batch
 * {@code setChunkForced(false)} sweep and anything else that forces or unforces chunks never touch them.
 *
 * <p>The margin is the room a test without padding really uses: the grid leaves 5 to 6 free blocks after each plot,
 * tests place their props a few blocks out from the origin, and some (a landing rocket, a docking station launcher)
 * keep their entity within 16 blocks of it.
 */
public final class TestChunks {

    private TestChunks() {}

    /** Blocks held around a test's structure at the least, whatever its padding. */
    static final int MARGIN = 16;

    /** A chunk is entity-ticking once every chunk within two of it is FULL. */
    private static final int RING = 2;

    /** Holds per level and chunk; a chunk is released when its count returns to zero. */
    private static final Map<ServerLevel, Long2IntOpenHashMap> COUNTS = new WeakHashMap<>();

    /**
     * Holds the chunks {@code minX..maxX, minZ..maxZ} entity-ticking until the returned action runs (it runs at most
     * once), and generates them and their ring on this thread. On 26.1 the hold also ends with the test whose origin is
     * {@code origin}, if it is one {@link #holdStart} holds.
     */
    static Runnable hold(ServerLevel level, BlockPos origin, int minX, int maxX, int minZ, int maxZ) {
        Range range = new Range(level, minX, maxX, minZ, maxZ);
        range.take();
        //? if >=26.1 {
        Hold owner = BY_ORIGIN.get(origin);
        if (owner != null) owner.extras.add(range);
        //?}
        return range::release;
    }

    /** A chunk's key in this class's own maps. */
    private static long key(int cx, int cz) {
        return (cx & 0xFFFFFFFFL) | (cz & 0xFFFFFFFFL) << 32;
    }

    /** Whether every chunk {@code minX..maxX, minZ..maxZ} is entity-ticking. */
    static boolean ticking(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!level.isPositionEntityTicking(new BlockPos(cx << 4, 0, cz << 4))) return false;
                //? if >=26.1 {
                if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) return false;
                //?}
            }
        }
        return true;
    }

    /** A rectangle of chunks counted in {@link #COUNTS} while it is taken. */
    private static class Range {

        final ServerLevel level;
        final int minX, maxX, minZ, maxZ;
        private boolean taken;

        Range(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
            this.level = level;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        void take() {
            if (taken) return;
            taken = true;
            Long2IntOpenHashMap counts = COUNTS.computeIfAbsent(level, l -> new Long2IntOpenHashMap());
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    if (counts.addTo(key(cx, cz), 1) == 0) acquire(level, cx, cz);
                }
            }
            // generated here, on the server thread: the ticket alone leaves the ring to the worker threads
            for (int cx = minX - RING; cx <= maxX + RING; cx++) {
                for (int cz = minZ - RING; cz <= maxZ + RING; cz++) level.getChunk(cx, cz);
            }
        }

        void release() {
            if (!taken) return;
            taken = false;
            Long2IntOpenHashMap counts = COUNTS.get(level);
            if (counts == null) return;
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    long key = key(cx, cz);
                    if (counts.addTo(key, -1) <= 1) {
                        counts.remove(key);
                        free(level, cx, cz);
                    }
                }
            }
        }

        int chunkCount() {
            return (maxX - minX + 1) * (maxZ - minZ + 1);
        }
    }

    //? if >=26.1 {
    /** Ticket radius 2: level {@code 33 - 2 = 31}, entity-ticking, as {@code ChunkMap.FORCED_TICKET_LEVEL}. */
    private static final int TICKET_RADIUS = 2;

    /** Level ticks a test is held at the most; past it, it starts anyway with one WARN (a hold must never hang a run). */
    private static final int MAX_HOLD_TICKS = 600;

    /** Loads, simulates and keeps the level active (a level with no player stops ticking entities otherwise); no timeout, never saved. */
    private static RegistryObject<TicketType> ticketType;

    private static final Map<GameTestInfo, Hold> HOLDS = new WeakHashMap<>();
    private static final Map<BlockPos, Hold> BY_ORIGIN = new HashMap<>();

    private static int gated, waited, maxWait, chunks;
    private static boolean announced;

    private static void acquire(ServerLevel level, int cx, int cz) {
        level.getChunkSource().addTicketWithRadius(ticketType.get(), new ChunkPos(cx, cz), TICKET_RADIUS);
        chunks++;
    }

    private static void free(ServerLevel level, int cx, int cz) {
        level.getChunkSource().removeTicketWithRadius(ticketType.get(), new ChunkPos(cx, cz), TICKET_RADIUS);
    }

    /** Called by {@link GameTests#function} while mods declare their tests, so the ticket type is registered with them. */
    static synchronized void declare() {
        if (ticketType != null) return;
        DeferredRegister<TicketType> register = DeferredRegister.create(Registries.TICKET_TYPE, GolemCore.MOD_ID);
        ticketType = register.register("game_test", () -> new TicketType(TicketType.NO_TIMEOUT,
                TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    }

    /**
     * Whether {@code info} must skip this tick: called at the head of {@code GameTestInfo.tick} (MixinGameTestChunks).
     * Only for tests declared through {@link GameTests}, only before the test has started, and never once it is done
     * or has failed (vanilla's own tick then reports the failure).
     */
    public static boolean holdStart(GameTestInfo info) {
        if (info.hasStarted() || info.isDone() || info.hasFailed() || ticketType == null) return false;
        Hold hold = HOLDS.get(info);
        if (hold == null) {
            if (!GameTests.declares(info.id().toString())) return false;
            TestInstanceBlockEntity block;
            try {
                block = info.getTestInstanceBlockEntity();
            } catch (IllegalStateException e) {
                return false; // vanilla's tick reports the missing block entity
            }
            BoundingBox box = block.getStructureBoundingBox().inflatedBy(Math.max(info.getTest().padding(), MARGIN));
            hold = new Hold(info.getLevel(), box.minX() >> 4, box.maxX() >> 4, box.minZ() >> 4, box.maxZ() >> 4);
            HOLDS.put(info, hold);
            hold.origin = block.getStartCorner();
            BY_ORIGIN.put(hold.origin, hold);
            info.addListener(hold);
            hold.take();
        }
        if (hold.ready) return false;
        if (ticking(hold.level, hold.minX, hold.maxX, hold.minZ, hold.maxZ)) {
            hold.ready = true;
            gated++;
            if (hold.ticks > 0) waited++;
            maxWait = Math.max(maxWait, hold.ticks);
            if (!announced) {
                announced = true;
                GolemCore.LOG.info("GameTest plot chunks: {} started after {} chunk(s) {}..{}, {}..{} were entity-ticking ({} tick(s) held)",
                        info.id(), hold.chunkCount(), hold.minX, hold.maxX, hold.minZ, hold.maxZ, hold.ticks);
            }
            return false;
        }
        if (++hold.ticks > MAX_HOLD_TICKS) {
            hold.ready = true;
            GolemCore.LOG.warn("GameTest plot chunks: {} started after {} ticks with chunks {}..{}, {}..{} not all entity-ticking",
                    info.id(), MAX_HOLD_TICKS, hold.minX, hold.maxX, hold.minZ, hold.maxZ);
            return false;
        }
        return true;
    }

    /** One test's plot; also the listener that releases it, and the extra chunks the test held, when the test ends. */
    private static final class Hold extends Range implements GameTestListener {

        final List<Range> extras = new ArrayList<>();
        BlockPos origin;
        boolean ready;
        int ticks;

        Hold(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
            super(level, minX, maxX, minZ, maxZ);
        }

        private void end(GameTestInfo info) {
            HOLDS.remove(info);
            if (BY_ORIGIN.get(origin) == this) BY_ORIGIN.remove(origin);
            for (Range extra : extras) extra.release();
            extras.clear();
            release();
            Long2IntOpenHashMap counts = COUNTS.get(level);
            if ((counts == null || counts.isEmpty()) && gated > 0) {
                // every held test of the batch has finished: one line per batch, the positive signal of this hold
                GolemCore.LOG.info("GameTest plot chunks: {} test(s) held until entity-ticking, {} waited (at most {} tick(s)), "
                        + "{} chunk ticket(s) taken and all released", gated, waited, maxWait, chunks);
                gated = waited = maxWait = chunks = 0;
            }
        }

        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { end(info); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { end(info); }
        @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {}
    }
    //?} else {
    /*/^* Chunks this class forced (a chunk forced already, by the runner or anyone else, is never unforced here). ^/
    private static final Map<ServerLevel, LongSet> OWNED = new WeakHashMap<>();

    private static void acquire(ServerLevel level, int cx, int cz) {
        if (level.setChunkForced(cx, cz, true)) OWNED.computeIfAbsent(level, l -> new LongOpenHashSet()).add(key(cx, cz));
    }

    private static void free(ServerLevel level, int cx, int cz) {
        LongSet owned = OWNED.get(level);
        if (owned != null && owned.remove(key(cx, cz))) level.setChunkForced(cx, cz, false);
    }
    *///?}
}
