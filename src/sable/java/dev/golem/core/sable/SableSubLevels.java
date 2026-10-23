package dev.golem.core.sable;

import dev.golem.core.world.SubLevels;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link SubLevels} answered by Sable, through Sable Companion (Sable's public compatibility API). Only built on
 * nodes that declare Sable (this source root is added by the build there) and only installed when the {@code sable}
 * mod is loaded, so nothing here is reached in a game without it.
 */
final class SableSubLevels implements SubLevels.Backend {

    /** Sable's NeoForge build embeds Flywheel visuals in sub-levels; its Fabric build does not. */
    private final boolean carriesInstancedVisuals;

    SableSubLevels(boolean carriesInstancedVisuals) {
        this.carriesInstancedVisuals = carriesInstancedVisuals;
    }

    @Override
    public boolean carriesInstancedVisuals() {
        return carriesInstancedVisuals;
    }

    /** Companion speaks metres per second; the game, and every caller of {@link SubLevels}, blocks per tick. */
    private static final double TICKS_PER_SECOND = 20.0;

    @Override
    public boolean isOnSubLevel(Level level, double x, double y, double z) {
        return SableCompanion.INSTANCE.getContaining(level, x, z) != null;
    }

    @Override
    public Vec3 toWorld(Level level, double x, double y, double z) {
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, new Vec3(x, y, z));
    }

    @Override
    public Vec3 velocity(Level level, double x, double y, double z) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, x, z);
        if (subLevel == null) return Vec3.ZERO;
        return SableCompanion.INSTANCE.getVelocity(level, subLevel, new Vec3(x, y, z)).scale(1.0 / TICKS_PER_SECOND);
    }

    @Override
    public Vec3 toWorldDirection(Level level, double x, double y, double z, Vec3 direction) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, x, z);
        if (subLevel == null) return direction;
        Vector3d turned = subLevel.logicalPose().orientation().transform(new Vector3d(direction.x, direction.y, direction.z));
        return new Vec3(turned.x, turned.y, turned.z);
    }

    @Override
    public Vec3 toLocalDirection(Level level, double x, double y, double z, Vec3 direction) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, x, z);
        if (subLevel == null) return direction;
        Vector3d turned = subLevel.logicalPose().orientation()
                .transformInverse(new Vector3d(direction.x, direction.y, direction.z));
        return new Vec3(turned.x, turned.y, turned.z);
    }

    @Override
    public SubLevels.RenderPose renderPose(Level level, double x, double y, double z, float partialTick) {
        // Client levels only: the client lookup reads the client's sub-level container.
        if (!level.isClientSide()) return null;
        ClientSubLevelAccess subLevel = SableCompanion.INSTANCE.getContainingClient(x, z);
        if (subLevel == null) return null;
        Pose3dc pose = subLevel.renderPose(partialTick);
        return new SubLevels.RenderPose(
                new Vec3(pose.position().x(), pose.position().y(), pose.position().z()),
                new Quaterniond(pose.orientation()),
                new Vec3(pose.rotationPoint().x(), pose.rotationPoint().y(), pose.rotationPoint().z()),
                new Vec3(pose.scale().x(), pose.scale().y(), pose.scale().z()));
    }

    @Override
    public List<SubLevels.Local> intoSubLevels(Level level, double x, double y, double z, double radius) {
        List<SubLevels.Local> found = new ArrayList<>();
        Vec3 world = new Vec3(x, y, z);
        for (SubLevelAccess subLevel : SableCompanion.INSTANCE.getAllIntersecting(level,
                new BoundingBox3d(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius))) {
            Pose3dc pose = subLevel.logicalPose();
            Vector3dc anchor = pose.rotationPoint();
            found.add(new SubLevels.Local(pose.transformPositionInverse(world),
                    new Vec3(anchor.x(), anchor.y(), anchor.z()), gridBox(pose, subLevel.boundingBox())));
        }
        return found;
    }

    private static AABB gridBox(Pose3dc pose, BoundingBox3dc world) {
        AABB bounds = null;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 at = pose.transformPositionInverse(new Vec3((corner & 1) == 0 ? world.minX() : world.maxX(),
                    (corner & 2) == 0 ? world.minY() : world.maxY(), (corner & 4) == 0 ? world.minZ() : world.maxZ()));
            bounds = bounds == null ? new AABB(at, at) : bounds.minmax(new AABB(at, at));
        }
        return bounds;
    }

    @Override
    public void push(Level level, double x, double y, double z, Vec3 force) {
        if (!(SableCompanion.INSTANCE.getContaining(level, x, z) instanceof ServerSubLevel subLevel)) return;
        // Weights to Sable's units: iron's mass times gravity, held for one tick.
        double weight = PhysicsBlockPropertyHelper.getMass(EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
                Blocks.IRON_BLOCK.defaultBlockState()) * DimensionPhysicsData.getGravity(level).length();
        impulse(subLevel, new Vector3d(x, y, z), force.scale(weight / TICKS_PER_SECOND));
    }

    @Override
    public void accelerate(Level level, double x, double y, double z, Vec3 deltaV) {
        if (!(SableCompanion.INSTANCE.getContaining(level, x, z) instanceof ServerSubLevel subLevel)) return;
        Vector3dc centre = subLevel.logicalPose().rotationPoint();
        impulse(subLevel, new Vector3d(centre), deltaV.scale(subLevel.getMassTracker().getMass() * TICKS_PER_SECOND));
    }

    /** Sable takes impulses in the sub-level's own frame, at a point of its plot. */
    private static void impulse(ServerSubLevel subLevel, Vector3d at, Vec3 worldImpulse) {
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) return;
        Vector3d local = subLevel.logicalPose().orientation()
                .transformInverse(new Vector3d(worldImpulse.x, worldImpulse.y, worldImpulse.z));
        handle.applyImpulseAtPoint(at, local);
    }

    @Override
    public Vec3 carriedVelocity(Entity entity) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(entity);
        if (subLevel == null) return Vec3.ZERO;
        Vec3 local = subLevel.logicalPose().transformPositionInverse(entity.position());
        return SableCompanion.INSTANCE.getVelocity(entity.level(), subLevel, local).scale(1.0 / TICKS_PER_SECOND);
    }

    /**
     * Sable splits a sub-level wherever its solid blocks stop touching, and assembles each piece separately. A
     * multiblock whose parts are not all solid could be cut in two that way; this adds every resolved part of a
     * structure to the piece that holds any part of it, so a structure always travels whole.
     */
    static void keepStructuresWhole() {
        SubLevelHeatMapManager.addSplitListener((level, bounds, blocks) -> blocks.addAll(missingParts(level, blocks)));
    }

    /** The non-air parts of structures {@code blocks} cuts into, closed over further structures. */
    static Set<BlockPos> missingParts(Level level, Iterable<BlockPos> blocks) {
        Set<BlockPos> present = new HashSet<>();
        for (BlockPos pos : blocks) present.add(pos.immutable());
        Set<BlockPos> added = new LinkedHashSet<>();
        Deque<BlockPos> open = new ArrayDeque<>(present);
        while (!open.isEmpty()) {
            for (BlockPos part : SubLevels.structurePartsWith(level, open.poll())) {
                BlockPos immutable = part.immutable();
                if (present.contains(immutable) || level.getBlockState(immutable).isAir()) continue;
                present.add(immutable);
                added.add(immutable);
                open.add(immutable);
            }
        }
        return added;
    }
}
