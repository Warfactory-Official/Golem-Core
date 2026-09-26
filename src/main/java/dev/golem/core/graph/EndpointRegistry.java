package dev.golem.core.graph;

import dev.golem.core.platform.Lifecycle;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;

/**
 * Cell -> owner claims (face mask + {@link Domains}), per level, not per graph: a machine states what a net can
 * serve instead of being probed. Claims are an optimisation, never a gate ({@link #resolve} re-validates).
 *
 * <p>Per-level map, not NEXT's {@code hbm$endpoints()} {@code ServerLevel} mixin; server thread only.
 * {@link #clearLevel} on level unload or the map pins the level.
 */
public final class EndpointRegistry {

    private static final Reference2ObjectOpenHashMap<Level, EndpointRegistry> LEVELS =
            new Reference2ObjectOpenHashMap<>();

    /**
     * Position claims no net probes (a link target may hold no block, far from its owner). Shared with {@link #LEVELS}
     * => unrelated claims on one coordinate overwrite each other.
     */
    private static final Reference2ObjectOpenHashMap<Level, EndpointRegistry> BEAMS =
            new Reference2ObjectOpenHashMap<>();

    private final Long2ObjectOpenHashMap<Endpoint> byCell = new Long2ObjectOpenHashMap<>();
    private final ChunkPositionIndex byChunk = new ChunkPositionIndex();

    public static EndpointRegistry of(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, key -> new EndpointRegistry());
    }

    /** @see #BEAMS */
    public static EndpointRegistry ofBeams(ServerLevel level) {
        return BEAMS.computeIfAbsent(level, key -> new EndpointRegistry());
    }

    static void install() {
        Lifecycle.onChunkUnload((level, chunk) -> {
            long chunkKey = ChunkPositionIndex.chunkOf(chunk.getPos().getWorldPosition().asLong());
            of(level).onChunkUnload(chunkKey);
            ofBeams(level).onChunkUnload(chunkKey);
        });
    }

    /** Drops a level's tables on unload; nothing else removes a level from the maps. */
    public static void clearLevel(Level level) {
        LEVELS.remove(level);
        BEAMS.remove(level);
    }

    /**
     * Loses nothing: a block entity re-declares every endpoint its chunk carries when the chunk comes
     * back. Without this the table is append-only for the life of the level.
     */
    public void onChunkUnload(long chunkKey) {
        LongOpenHashSet cells = byChunk.take(chunkKey);
        if (cells == null) return;
        for (LongIterator it = cells.iterator(); it.hasNext(); ) byCell.remove(it.nextLong());
    }

    /**
     * Is the memoised chunk still at FULL status? {@code getFullStatus} reads the holder's live
     * ticket level, which {@code !isRemoved()} on the block entity does not imply.
     */
    private static boolean stillFull(@Nullable WeakReference<LevelChunk> memo) {
        LevelChunk chunk = memo == null ? null : memo.get();
        return chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL);
    }

    private static @Nullable LevelChunk readableChunk(ServerLevel level, int x, int z) {
        return level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
    }

    /**
     * Declares {@code cell} as exposing {@code faceMask} on behalf of {@code owner}. An unchanged
     * declaration is left alone rather than replaced: a block entity re-declares on every chunk load,
     * and a machine whose ports are its own footprint re-declares every tick, which would otherwise
     * throw away the resolution this entry has already memoised.
     */
    public void declare(BlockPos cell, BlockPos owner, int faceMask, int domains) {
        long cellKey = cell.asLong();
        if (faceMask == 0) {
            byCell.remove(cellKey);
            byChunk.remove(cellKey);
            return;
        }
        Endpoint held = byCell.get(cellKey);
        if (held != null && held.owner() == owner.asLong() && held.faceMask() == faceMask
                && held.domains() == domains) {
            return;
        }
        byCell.put(cellKey, new Endpoint(owner.asLong(), faceMask, domains));
        byChunk.add(cellKey);
    }

    public void withdraw(BlockPos cell) {
        byCell.remove(cell.asLong());
        byChunk.remove(cell.asLong());
    }

    /** The raw declaration, unvalidated, for bookkeeping. Transfer paths want {@link #resolve}. */
    public @Nullable Endpoint declared(long cellKey) {
        return byCell.get(cellKey);
    }

    /**
     * The live owner exposed at {@code cellKey} on {@code face} for one of {@code domains}, or null.
     * The declaration is an optimisation, never a gate.
     */
    public @Nullable BlockEntity resolve(ServerLevel level, long cellKey, @Nullable Direction face, int domains) {
        Endpoint ep = byCell.get(cellKey);
        if (ep == null) return null;
        if ((ep.domains() & domains) == 0) return null;
        if (face != null && !ep.isOpen(face)) return null;
        WeakReference<BlockEntity> memo = ep.resolved;
        BlockEntity cached = memo == null ? null : memo.get();
        if (cached != null && !cached.isRemoved() && stillFull(ep.resolvedChunk)) return cached;
        BlockPos owner = BlockPos.of(ep.owner());
        LevelChunk chunk = readableChunk(level, owner.getX(), owner.getZ());
        if (chunk == null) {
            ep.resolved = null;
            ep.resolvedChunk = null;
            return null;
        }
        BlockEntity be = chunk.getBlockEntity(owner);
        if (be == null || be.isRemoved()) {
            ep.resolved = null;
            ep.resolvedChunk = null;
            return null;
        }
        ep.resolved = new WeakReference<>(be);
        ep.resolvedChunk = new WeakReference<>(chunk);
        return be;
    }

    public int size() {
        return byCell.size();
    }

    /**
     * One declared exposure: owner, a {@code 1 << Direction.ordinal()} face mask, and the
     * {@link Domains}.
     */
    public static final class Endpoint {

        private final long owner;
        private final int faceMask;
        private final int domains;

        /**
         * The last owner that resolved, weak on purpose: a strong field would pin a removed block
         * entity, and its inventories, until something resolved that cell again — which can be never.
         */
        private @Nullable WeakReference<BlockEntity> resolved;

        /** The owner's chunk, memoised alongside it, because a live block entity can sit in an unreadable chunk. */
        private @Nullable WeakReference<LevelChunk> resolvedChunk;

        public Endpoint(long owner, int faceMask, int domains) {
            this.owner = owner;
            this.faceMask = faceMask;
            this.domains = domains;
        }

        public long owner() {
            return owner;
        }

        public int faceMask() {
            return faceMask;
        }

        public int domains() {
            return domains;
        }

        public boolean isOpen(Direction face) {
            return (faceMask & (1 << face.ordinal())) != 0;
        }
    }
}
