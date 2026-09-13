package dev.golem.core.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/**
 * A mod's own bytes, stored per chunk in their own region files beside vanilla's.
 *
 * <p>The alternative is a per-level {@code SavedData} blob keyed by chunk, and it degrades badly: the
 * whole map is read at level load and rewritten in full at every autosave, so the cost of saving one
 * chunk's worth of change grows with the number of chunks that have ever been written. A region file
 * pays per chunk, which is what the data actually is.
 *
 * <p>Vanilla stores entities and points of interest exactly this way and the machinery is reused rather
 * than reimplemented: {@link IOWorker} owns the region cache, the write coalescing and the io-pool
 * thread, and is subclassed because its constructor is {@code protected} on every supported version —
 * the region storage underneath it is package-private, so this is the narrowest public door in.
 *
 * <p>Reads are futures on purpose. The caller is expected to start one when it learns a chunk is
 * arriving and take the value at first use, so the read overlaps a tick of other work instead of
 * stalling the server thread inside the chunk-load callback.
 *
 * <p>Payloads are opaque. What a chunk's bytes mean, and how they survive a format change, belongs to
 * whoever wrote them.
 *
 * <p>A store nothing has written does not exist on disk, and reading from one does not create it.
 * Opening a region file creates it — an empty header and a file handle — so a read is answered from the
 * region's absence instead, and a world that never produces this data never grows a folder for it.
 * Reads and writes are expected on the server thread, which is where chunks arrive and leave.
 */
public final class ChunkSidecar implements AutoCloseable {

    private static final String PAYLOAD = "p";

    private final Worker worker;
    private final Path root;
    private final LongSet regionsPresent = new LongOpenHashSet();
    private final LongSet regionsAbsent = new LongOpenHashSet();

    private ChunkSidecar(Worker worker, Path root) {
        this.worker = worker;
        this.root = root;
    }

    /**
     * Opens (creating on first write) the store named {@code name} for one level. The folder sits beside
     * the dimension's {@code region}, {@code entities} and {@code poi} directories, so a world's extra
     * data travels with it — including through a dimension folder being copied or deleted wholesale.
     */
    public static ChunkSidecar open(ServerLevel level, String name) {
        MinecraftServer server = level.getServer();
        Path root = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT))
                .resolve(name);
        return new ChunkSidecar(new Worker(level, root, name, server.forceSynchronousWrites()), root);
    }

    /** The chunk's payload, or null where it has none. Completes on the io pool. */
    public CompletableFuture<byte[]> read(ChunkPos pos) {
        if (!regionExists(pos)) return CompletableFuture.completedFuture(null);
        return worker.loadAsync(pos).thenApply(ChunkSidecar::payload);
    }

    /** Queues a payload for the chunk; null or empty removes the chunk's entry instead. */
    public void write(ChunkPos pos, byte[] payload) {
        if (payload == null || payload.length == 0) {
            if (!regionExists(pos)) return;
            worker.store(pos, (CompoundTag) null);
            return;
        }
        long region = regionKey(pos);
        regionsAbsent.remove(region);
        regionsPresent.add(region);
        CompoundTag tag = new CompoundTag();
        tag.putByteArray(PAYLOAD, payload);
        worker.store(pos, tag);
    }

    /** Blocks until every queued write has reached the disk. */
    public void flush() {
        worker.synchronize(true).join();
    }

    /** Flushes, then releases the region files. The store is unusable afterwards. */
    @Override
    public void close() {
        try {
            worker.close();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Whether this chunk's region file is there. Remembered per region rather than restated per chunk:
     * the answer only changes when this store writes, and this store is what changes it.
     */
    private boolean regionExists(ChunkPos pos) {
        long region = regionKey(pos);
        if (regionsPresent.contains(region)) return true;
        if (regionsAbsent.contains(region)) return false;
        boolean exists = Files.isRegularFile(root.resolve(
                "r." + pos.getRegionX() + "." + pos.getRegionZ()
                        + net.minecraft.world.level.chunk.storage.RegionFileStorage.ANVIL_EXTENSION));
        (exists ? regionsPresent : regionsAbsent).add(region);
        return exists;
    }

    private static long regionKey(ChunkPos pos) {
        return (pos.getRegionX() & 0xFFFFFFFFL) | ((long) pos.getRegionZ() << 32);
    }

    private static byte[] payload(Optional<CompoundTag> tag) {
        if (tag.isEmpty()) return null;
        //? if >=26.1 {
        byte[] bytes = tag.get().getByteArray(PAYLOAD).orElse(null);
        //?} else {
        /*byte[] bytes = tag.get().getByteArray(PAYLOAD);
        *///?}
        return bytes == null || bytes.length == 0 ? null : bytes;
    }

    /**
     * 1.20.1 names the worker directly and has no {@code RegionStorageInfo}; from 1.20.5 the record
     * carries the same name plus the dimension, which is what vanilla's IO error reporting reads back.
     */
    private static final class Worker extends IOWorker {

        Worker(ServerLevel level, Path root, String name, boolean syncWrites) {
            //? if >=1.20.5 {
            super(new net.minecraft.world.level.chunk.storage.RegionStorageInfo(
                    level.getServer().getWorldData().getLevelName(), level.dimension(), name), root, syncWrites);
            //?} else {
            /*super(root, syncWrites, name);
            *///?}
        }
    }
}
