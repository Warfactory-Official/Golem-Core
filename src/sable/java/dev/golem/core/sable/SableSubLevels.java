package dev.golem.core.sable;

import dev.golem.core.world.SubLevels;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
