package com.wf.gemrender.particle;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The world half of {@link ParticleCollision}: a {@link ParticleCollision.Probe} backed by a real level.
 *
 * <p>Built around the fact that a thousand particles from one explosion sweep the same few hundred blocks.
 * Each segment is walked block by block rather than handed to {@code BlockGetter.clip}, and what each block
 * turned out to be is remembered, so the second particle through a patch of ground pays an array index
 * where the first paid a chunk lookup and a shape build. That is the whole reason a burst this size fits in
 * a frame; see {@code docs/INTEGRATION.md}, "What it costs".
 *
 * <p>A full cube (almost everything a particle ever lands on) is answered from the walk itself, with the
 * face it entered through. Only a partial shape (a slab, a fence, a stair) falls back to clipping the real
 * {@link VoxelShape}, which is exact and rare.
 *
 * <p>It is the block collision shapes that are consulted, not the visual ones, so a particle settles on the
 * surface a player would stand on. Fluids are ignored: a particle that stops at the surface of water reads
 * as a bug, and one that sinks reads as water.
 *
 * <p>Short-lived by design: the cache is a snapshot of a world that can be mined. Hold one for a burst, not
 * for an effect. {@link ParticleEmitter} keeps one only for as long as the clock does not move.
 *
 * <p>Does block lookups, so it belongs on the thread that owns the level. Spawn from a tick or an event
 * handler, not from a visual's constructor: Flywheel builds those on its own task threads.
 */
public final class LevelContactProbe implements ParticleCollision.Probe {

    private static final byte UNKNOWN = 0;
    private static final byte EMPTY = 1;
    private static final byte FULL = 2;
    private static final byte PARTIAL = 3;

    /** A walk this long means the caller handed in a segment far longer than a sweep ever produces. */
    private static final int MAX_BLOCKS = 256;

    /** Cleared rather than grown past this, so one long-lived probe cannot become a leak. */
    private static final int MAX_CACHED_BLOCKS = 16384;

    private final BlockGetter level;

    private final Long2ByteOpenHashMap classes = new Long2ByteOpenHashMap();

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private LevelContactProbe(BlockGetter level) {
        this.level = level;
    }

    /** @return a probe reading {@code level}, or {@code null} for a null level, which predicts no contact */
    public static ParticleCollision.Probe of(BlockGetter level) {
        return level == null ? null : new LevelContactProbe(level);
    }

    /**
     * The face of a block, in {@link ParticleCollision}'s encoding.
     *
     * <p>Spelled out rather than taken from {@code Direction.ordinal()} so that the two orderings are free to
     * disagree: one of them is vanilla's and can move underneath this.
     */
    public static int encode(Direction direction) {
        switch (direction) {
            case WEST:
                return ParticleCollision.MINUS_X;
            case EAST:
                return ParticleCollision.PLUS_X;
            case DOWN:
                return ParticleCollision.MINUS_Y;
            case NORTH:
                return ParticleCollision.MINUS_Z;
            case SOUTH:
                return ParticleCollision.PLUS_Z;
            case UP:
            default:
                return ParticleCollision.PLUS_Y;
        }
    }

    /** Forgets what the world looked like. */
    public void reset() {
        classes.clear();
    }

    @Override
    public boolean probe(double fromX, double fromY, double fromZ, double toX, double toY, double toZ,
                         ParticleCollision.Hit into) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;

        int x = Mth.floor(fromX);
        int y = Mth.floor(fromY);
        int z = Mth.floor(fromZ);
        int endX = Mth.floor(toX);
        int endY = Mth.floor(toY);
        int endZ = Mth.floor(toZ);

        int stepX = step(dx);
        int stepY = step(dy);
        int stepZ = step(dz);

        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

        double tMaxX = firstCrossing(fromX, dx, x, stepX);
        double tMaxY = firstCrossing(fromY, dy, y, stepY);
        double tMaxZ = firstCrossing(fromZ, dz, z, stepZ);

        // The face of the block the walk is currently standing in, and when it got there. Negative until the
        // walk has crossed anything, which is the case where the segment starts inside a block already.
        int entryFace = -1;
        double entryTime = 0.0;

        for (int visited = 0; visited < MAX_BLOCKS; visited++) {
            byte kind = classify(x, y, z);

            if (kind == FULL) {
                into.fraction = (float) entryTime;
                into.normal = entryFace >= 0 ? entryFace : facing(dx, dy, dz);
                return true;
            }
            if (kind == PARTIAL && clipShape(x, y, z, fromX, fromY, fromZ, toX, toY, toZ, into)) {
                return true;
            }

            if (x == endX && y == endY && z == endZ) {
                return false;
            }

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                entryTime = tMaxX;
                tMaxX += tDeltaX;
                x += stepX;
                entryFace = stepX > 0 ? ParticleCollision.MINUS_X : ParticleCollision.PLUS_X;
            } else if (tMaxY <= tMaxZ) {
                entryTime = tMaxY;
                tMaxY += tDeltaY;
                y += stepY;
                entryFace = stepY > 0 ? ParticleCollision.MINUS_Y : ParticleCollision.PLUS_Y;
            } else {
                entryTime = tMaxZ;
                tMaxZ += tDeltaZ;
                z += stepZ;
                entryFace = stepZ > 0 ? ParticleCollision.MINUS_Z : ParticleCollision.PLUS_Z;
            }

            if (entryTime > 1.0) {
                return false;
            }
        }

        return false;
    }

    private byte classify(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        byte known = classes.get(key);
        if (known != UNKNOWN) {
            return known;
        }

        cursor.set(x, y, z);
        BlockState state = level.getBlockState(cursor);

        byte kind;
        if (state.isAir()) {
            kind = EMPTY;
        } else if (state.getCollisionShape(level, cursor)
                .isEmpty()) {
            kind = EMPTY;
        } else if (state.isCollisionShapeFullBlock(level, cursor)) {
            kind = FULL;
        } else {
            kind = PARTIAL;
        }

        if (classes.size() >= MAX_CACHED_BLOCKS) {
            classes.clear();
        }
        classes.put(key, kind);
        return kind;
    }

    private boolean clipShape(int x, int y, int z, double fromX, double fromY, double fromZ, double toX,
                              double toY, double toZ, ParticleCollision.Hit into) {
        cursor.set(x, y, z);
        VoxelShape shape = level.getBlockState(cursor)
                .getCollisionShape(level, cursor);

        Vec3 from = new Vec3(fromX, fromY, fromZ);
        Vec3 to = new Vec3(toX, toY, toZ);
        BlockHitResult hit = shape.clip(from, to, cursor);
        if (hit == null) {
            return false;
        }

        double length = from.distanceTo(to);
        into.fraction = length > 1.0e-6 ? (float) (from.distanceTo(hit.getLocation()) / length) : 0.0f;
        into.normal = encode(hit.getDirection());
        return true;
    }

    private static int step(double delta) {
        return delta > 0.0 ? 1 : delta < 0.0 ? -1 : 0;
    }

    /** Where along the segment the walk leaves the starting block on one axis, in units of the segment. */
    private static double firstCrossing(double from, double delta, int block, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? block + 1 : block;
        return (boundary - from) / delta;
    }

    /** The face a segment would have entered through, for a segment that started inside a block already. */
    private static int facing(double dx, double dy, double dz) {
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);

        if (ax >= ay && ax >= az) {
            return dx > 0.0 ? ParticleCollision.MINUS_X : ParticleCollision.PLUS_X;
        }
        if (ay >= az) {
            return dy > 0.0 ? ParticleCollision.MINUS_Y : ParticleCollision.PLUS_Y;
        }
        return dz > 0.0 ? ParticleCollision.MINUS_Z : ParticleCollision.PLUS_Z;
    }
}
