package dev.golem.core.world;

import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.joml.Quaterniondc;

/**
 * Moving block structures ("sub-levels"): blocks that live at one position in the level's block grid but are
 * simulated, drawn and interacted with at another pose in the world. Sable (Create Aeronautics) is the one such
 * system today; its blocks sit in a "plot" millions of blocks from where the structure is seen.
 *
 * <p>A block entity on a sub-level must therefore never compare its {@code worldPosition} with a player, entity
 * or another machine by raw distance, nor hand that position to anything that lives in the world (an entity it
 * spawns, a radar scan, a target): it goes through {@link #toWorld} first. Off a sub-level every method here is
 * the identity, and on a node or in a game without such a system the backend is never replaced.
 */
public final class SubLevels {

    private SubLevels() {}

    /** The system's answers. Installed by the integration that owns it; the default is "no sub-levels". */
    public interface Backend {
        /** Whether {@code (x, y, z)}, in block-grid coordinates, lies inside a sub-level. */
        boolean isOnSubLevel(Level level, double x, double y, double z);

        /** Where block-grid point {@code (x, y, z)} currently is in the world; itself when not on a sub-level. */
        Vec3 toWorld(Level level, double x, double y, double z);

        /** World velocity of block-grid point {@code (x, y, z)}, in blocks per tick; zero when not on a sub-level. */
        Vec3 velocity(Level level, double x, double y, double z);

        /**
         * {@code direction}, given in the frame of the sub-level holding block-grid point {@code (x, y, z)} (its blocks'
         * axes), as a world direction. Unchanged when the point is on no sub-level. Turns only; never scales.
         */
        default Vec3 toWorldDirection(Level level, double x, double y, double z, Vec3 direction) {
            return direction;
        }

        /** The inverse of {@link #toWorldDirection}: a world direction in the frame of that sub-level. */
        default Vec3 toLocalDirection(Level level, double x, double y, double z, Vec3 direction) {
            return direction;
        }

        /**
         * Whether instanced visuals (Flywheel) of block entities on a sub-level are drawn with the sub-level. When not,
         * the engine draws them where their blocks sit in the block grid, far from where the structure is seen, so
         * the block entity's own renderer has to draw them instead.
         */
        default boolean carriesInstancedVisuals() {
            return true;
        }

        /**
         * Client only: where the sub-level holding block-grid point {@code (x, y, z)} is drawn this frame, or null when
         * the point is on none. Rendering's counterpart of {@link #toWorld}, interpolated to {@code partialTick}.
         */
        default @org.jspecify.annotations.Nullable RenderPose renderPose(Level level, double x, double y, double z,
                                                                          float partialTick) {
            return null;
        }

        /** World point {@code (x, y, z)} as seen from every sub-level whose bounds come within {@code radius} of it. */
        default List<Local> intoSubLevels(Level level, double x, double y, double z, double radius) {
            return List.of();
        }

        /** Server only: pushes a sub-level at its grid point for one tick; world force in iron-block weights. */
        default void push(Level level, double x, double y, double z, Vec3 force) {}

        /** Server only: adds {@code deltaV} (world, blocks per tick) to a whole sub-level's velocity, unturned. */
        default void accelerate(Level level, double x, double y, double z, Vec3 deltaV) {}

        /** World velocity in blocks per tick of the sub-level {@code entity} stands on or rides; zero when none. */
        default Vec3 carriedVelocity(Entity entity) {
            return Vec3.ZERO;
        }
    }

    /** A world point in one sub-level's grid, its centre of mass ({@code anchor}) and a grid box around its blocks. */
    public record Local(Vec3 point, Vec3 anchor, AABB blocks) {}

    /**
     * A sub-level's pose as drawn: a block-grid point {@code p} appears in the world at
     * {@code orientation * (scale * (p - rotationPoint)) + position}. {@code dev.golem.core.render.SubLevelPoses} turns it
     * into a {@code PoseStack} transform.
     */
    public record RenderPose(Vec3 position, Quaterniondc orientation, Vec3 rotationPoint, Vec3 scale) {}

    /**
     * Names the blocks that must stay together with {@code pos} whenever a sub-level system splits or carries
     * blocks: every other part of the multiblock {@code pos} belongs to, in block-grid coordinates. Returns
     * an empty collection for a block that is not part of a larger structure.
     */
    @FunctionalInterface
    public interface StructureResolver {
        Collection<BlockPos> partsWith(Level level, BlockPos pos);
    }

    /**
     * One batch of blocks a sub-level system carried to new positions: assembling blocks into a sub-level, splitting
     * one, or setting one back down into the world. The system copies each block entity's saved data to the new
     * position and then clears the old one, so anything a block keeps outside its own saved data (positions of
     * other blocks, a place in a network or an index) is for the mod to carry over in a {@link MoveListener}.
     *
     * @param moved     each carried block's old position mapped to its new one, in batch order
     * @param transform where any old position lands under this move (defined for blocks that were not carried too)
     * @param rotation  how the batch was turned; block states have already been rotated by it
     */
    public record Move(ServerLevel from, ServerLevel to, Map<BlockPos, BlockPos> moved,
                       UnaryOperator<BlockPos> transform, Rotation rotation) {

        /** Whether the block at {@code old} was carried by this move. */
        public boolean carried(BlockPos old) {
            return moved.containsKey(old);
        }

        /** Where {@code old} lands under this move. */
        public BlockPos apply(BlockPos old) {
            BlockPos carried = moved.get(old);
            return carried != null ? carried : transform.apply(old);
        }
    }

    /** Told after each {@link Move}, once every block is at its new position and every old position is cleared. */
    @FunctionalInterface
    public interface MoveListener {
        void afterMove(Move move);
    }

    /**
     * A mod's block masses, in vanilla iron blocks (the system's own mass for {@code minecraft:iron_block} is the
     * unit). NaN for a block the rule does not cover. The system's own data for a block, by name or tag, wins.
     */
    @FunctionalInterface
    public interface MassRule {
        double inIronBlocks(Block block);
    }

    private static final Backend NONE = new Backend() {
        @Override
        public boolean isOnSubLevel(Level level, double x, double y, double z) {
            return false;
        }

        @Override
        public Vec3 toWorld(Level level, double x, double y, double z) {
            return new Vec3(x, y, z);
        }

        @Override
        public Vec3 velocity(Level level, double x, double y, double z) {
            return Vec3.ZERO;
        }
    };

    private static volatile Backend backend = NONE;
    private static final List<StructureResolver> RESOLVERS = new ArrayList<>();
    private static final List<MoveListener> MOVE_LISTENERS = new ArrayList<>();
    private static final List<MassRule> MASS_RULES = new CopyOnWriteArrayList<>();
    /** Levels a move is under way in (from and to), innermost last; a move can start inside another. */
    private static final List<Level> MOVING = new CopyOnWriteArrayList<>();

    /** Installs the sub-level system's backend. Called once, by the integration, during setup. */
    public static void install(Backend system) {
        backend = system;
    }

    /** Whether a sub-level system is installed in this game. */
    public static boolean present() {
        return backend != NONE;
    }

    /** Registers a {@link StructureResolver}; a mod with multiblocks states its structures once, at setup. */
    public static void addStructureResolver(StructureResolver resolver) {
        synchronized (RESOLVERS) {
            RESOLVERS.add(resolver);
        }
    }

    /** Every registered resolver's parts for {@code pos} (for the backend; never includes {@code pos} itself twice). */
    public static Collection<BlockPos> structurePartsWith(Level level, BlockPos pos) {
        List<BlockPos> parts = new ArrayList<>();
        synchronized (RESOLVERS) {
            for (StructureResolver resolver : RESOLVERS) parts.addAll(resolver.partsWith(level, pos));
        }
        return parts;
    }

    /** Registers a {@link MoveListener}; a mod states once, at setup, how its blocks follow a move. */
    public static void addMoveListener(MoveListener listener) {
        synchronized (MOVE_LISTENERS) {
            MOVE_LISTENERS.add(listener);
        }
    }

    /** Registers a {@link MassRule}; a mod states once, at setup, what its blocks weigh. */
    public static void addMassRule(MassRule rule) {
        MASS_RULES.add(rule);
    }

    /** For a backend: the first rule's mass for {@code block} in iron blocks, NaN when no rule covers it. */
    public static double massInIronBlocks(Block block) {
        for (MassRule rule : MASS_RULES) {
            double mass = rule.inIronBlocks(block);
            if (!Double.isNaN(mass)) return mass;
        }
        return Double.NaN;
    }

    /**
     * Whether a sub-level system is moving blocks in {@code level} right now. A block or block entity removed while
     * this holds is being carried, not destroyed: it must not drop its contents, tear its structure down or put
     * anything back at the old position.
     */
    public static boolean isMoving(Level level) {
        // Moves happen on the server thread, inside one call; a worldgen thread asking only ever reads.
        if (MOVING.isEmpty() || level == null || level.isClientSide()) return false;
        for (Level moving : MOVING) if (moving == level) return true;
        return false;
    }

    /** For a backend: a batch is about to move from {@code from} to {@code to}. Pair with {@link #endMove}. */
    public static void beginMove(ServerLevel from, ServerLevel to) {
        MOVING.add(from);
        MOVING.add(to);
    }

    /** For a backend: the batch {@link #beginMove} announced has moved; tells every {@link MoveListener}. */
    public static void endMove(Move move) {
        try {
            Move view = new Move(move.from(), move.to(), Collections.unmodifiableMap(move.moved()), move.transform(),
                    move.rotation());
            List<MoveListener> listeners;
            synchronized (MOVE_LISTENERS) {
                listeners = List.copyOf(MOVE_LISTENERS);
            }
            for (MoveListener listener : listeners) listener.afterMove(view);
        } finally {
            MOVING.remove(MOVING.size() - 1);
            MOVING.remove(MOVING.size() - 1);
        }
    }

    public static boolean isOnSubLevel(Level level, BlockPos pos) {
        return backend.isOnSubLevel(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static boolean isOnSubLevel(Level level, Vec3 pos) {
        return backend.isOnSubLevel(level, pos.x, pos.y, pos.z);
    }

    /** World position of a block-grid point; the identity off a sub-level, and for a null level (an item render). */
    public static Vec3 toWorld(@org.jspecify.annotations.Nullable Level level, double x, double y, double z) {
        return level == null ? new Vec3(x, y, z) : backend.toWorld(level, x, y, z);
    }

    public static Vec3 toWorld(Level level, Vec3 pos) {
        return backend.toWorld(level, pos.x, pos.y, pos.z);
    }

    /** World position of a block's centre. */
    public static Vec3 toWorld(Level level, BlockPos pos) {
        return backend.toWorld(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** The block a world point falls in, as the world sees it: {@code pos} itself off a sub-level. */
    public static BlockPos toWorldBlock(Level level, BlockPos pos) {
        if (!isOnSubLevel(level, pos)) return pos;
        return BlockPos.containing(toWorld(level, pos));
    }

    /** World velocity of a block-grid point in blocks per tick; zero off a sub-level. */
    public static Vec3 velocity(Level level, Vec3 pos) {
        return backend.velocity(level, pos.x, pos.y, pos.z);
    }

    /**
     * A direction in the frame of the structure {@code pos} stands on (a turret's aim, a barrel), as a world direction:
     * the structure's own axes when {@code pos} is on a sub-level, unchanged otherwise.
     */
    public static Vec3 toWorldDirection(Level level, BlockPos pos, Vec3 direction) {
        return backend.toWorldDirection(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, direction);
    }

    /** A world direction in the frame of the structure {@code pos} stands on; unchanged off a sub-level. */
    public static Vec3 toLocalDirection(Level level, BlockPos pos, Vec3 direction) {
        return backend.toLocalDirection(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, direction);
    }

    /**
     * Whether an instanced visual (Flywheel) of the block entity at {@code pos} would be drawn in the wrong place: the
     * block is on a sub-level whose system does not carry instanced visuals with it. Its renderer must draw it then.
     */
    public static boolean strandsInstancedVisuals(@org.jspecify.annotations.Nullable Level level, BlockPos pos) {
        return level != null && !backend.carriesInstancedVisuals() && isOnSubLevel(level, pos);
    }

    /** Client only: how the sub-level holding block-grid point {@code (x, y, z)} is drawn this frame; null off one. */
    public static @org.jspecify.annotations.Nullable RenderPose renderPose(Level level, double x, double y, double z,
                                                                            float partialTick) {
        return backend.renderPose(level, x, y, z, partialTick);
    }

    public static List<Local> intoSubLevels(Level level, Vec3 world, double radius) {
        return backend.intoSubLevels(level, world.x, world.y, world.z, radius);
    }

    public static void push(Level level, Vec3 gridPoint, Vec3 force) {
        backend.push(level, gridPoint.x, gridPoint.y, gridPoint.z, force);
    }

    public static void accelerate(Level level, Vec3 gridPoint, Vec3 deltaV) {
        backend.accelerate(level, gridPoint.x, gridPoint.y, gridPoint.z, deltaV);
    }

    public static Vec3 carriedVelocity(Entity entity) {
        return backend.carriedVelocity(entity);
    }

    /** The world box around a block-grid box: the box itself off a sub-level, else the bounds of its turned corners. */
    public static AABB toWorld(Level level, AABB box) {
        if (!isOnSubLevel(level, box.getCenter())) return box;
        AABB bounds = null;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 at = toWorld(level, (corner & 1) == 0 ? box.minX : box.maxX, (corner & 2) == 0 ? box.minY : box.maxY,
                    (corner & 4) == 0 ? box.minZ : box.maxZ);
            bounds = bounds == null ? new AABB(at, at) : bounds.minmax(new AABB(at, at));
        }
        return bounds;
    }

    /** Squared world distance between two block-grid points, either of which may be on a sub-level. */
    public static double distanceSq(Level level, Vec3 a, Vec3 b) {
        return toWorld(level, a).distanceToSqr(toWorld(level, b));
    }

    /** As {@link #distanceSq(Level, Vec3, Vec3)}; a null level (an item render) measures plainly. */
    public static double distanceSq(@org.jspecify.annotations.Nullable Level level, double ax, double ay, double az,
                                    double bx, double by, double bz) {
        return toWorld(level, ax, ay, az).distanceToSqr(toWorld(level, bx, by, bz));
    }
}
