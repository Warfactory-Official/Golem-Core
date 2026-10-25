package dev.golem.core.world;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A union of boxes that picks like 1.7's per-box {@code collisionRayTrace}: the first box in declaration order that
 * the ray meets wins, not the nearest. The hit names the footprint cell holding the hit point, so the server's
 * cell-distance check accepts it whichever cell of a multiblock returned the shape. Collision uses the plain union.
 */
public final class OrderedShape extends ArrayVoxelShape {
    private final AABB[] boxes;
    private final AABB footprint;
    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private final double dx;
    private final double dy;
    private final double dz;

    private OrderedShape(DiscreteVoxelShape grid, double[] xs, double[] ys, double[] zs, AABB[] boxes,
                         AABB footprint, double dx, double dy, double dz) {
        super(grid, xs, ys, zs);
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.boxes = boxes;
        this.footprint = footprint;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    /** Boxes and footprint are in the frame of the block the shape is returned for; the footprint bounds hit cells. */
    public static OrderedShape of(AABB footprint, AABB... boxes) {
        double[] xs = edges(boxes, Direction.Axis.X), ys = edges(boxes, Direction.Axis.Y), zs = edges(boxes, Direction.Axis.Z);
        var grid = new BitSetDiscreteVoxelShape(xs.length - 1, ys.length - 1, zs.length - 1);
        for (int i = 0; i < xs.length - 1; i++) {
            for (int j = 0; j < ys.length - 1; j++) {
                for (int k = 0; k < zs.length - 1; k++) {
                    double x = (xs[i] + xs[i + 1]) / 2, y = (ys[j] + ys[j + 1]) / 2, z = (zs[k] + zs[k + 1]) / 2;
                    for (AABB box : boxes) {
                        if (box.contains(x, y, z)) {
                            grid.fill(i, j, k);
                            break;
                        }
                    }
                }
            }
        }
        return new OrderedShape(grid, xs, ys, zs, boxes.clone(), footprint, 0, 0, 0);
    }

    private static double[] edges(AABB[] boxes, Direction.Axis axis) {
        double[] all = new double[boxes.length * 2];
        for (int i = 0; i < boxes.length; i++) {
            all[2 * i] = boxes[i].min(axis);
            all[2 * i + 1] = boxes[i].max(axis);
        }
        Arrays.sort(all);
        int n = 0;
        for (double v : all) if (n == 0 || v != all[n - 1]) all[n++] = v;
        return Arrays.copyOf(all, n);
    }

    private static double[] offset(double[] coords, double by) {
        double[] out = coords.clone();
        for (int i = 0; i < out.length; i++) out[i] += by;
        return out;
    }

    @Override
    public VoxelShape move(double x, double y, double z) {
        if (x == 0 && y == 0 && z == 0) return this;
        return new OrderedShape(shape, offset(xs, x), offset(ys, y), offset(zs, z), boxes, footprint,
                dx + x, dy + y, dz + z);
    }

    // The grid is already the union; vanilla's rebuild would return a plain shape and lose the ordered pick.
    @Override
    public VoxelShape optimize() {
        return this;
    }

    @Override
    public @Nullable BlockHitResult clip(Vec3 from, Vec3 to, BlockPos pos) {
        double x = pos.getX() + dx, y = pos.getY() + dy, z = pos.getZ() + dz;
        Vec3 start = from.subtract(x, y, z);
        Vec3 end = to.subtract(x, y, z);
        for (AABB box : boxes) {
            BlockHitResult hit = intercept(box, start, end);
            if (hit == null) continue;
            Vec3 point = hit.getLocation().add(x, y, z);
            // Clamped in world space: a local nextDown is lost once the block position is added.
            BlockPos cell = BlockPos.containing(
                    Mth.clamp(point.x, x + footprint.minX, Math.nextDown(x + footprint.maxX)),
                    Mth.clamp(point.y, y + footprint.minY, Math.nextDown(y + footprint.maxY)),
                    Mth.clamp(point.z, z + footprint.minZ, Math.nextDown(z + footprint.maxZ)));
            return new BlockHitResult(point, hit.getDirection(), cell, false);
        }
        return null;
    }

    /** 1.7 {@code AxisAlignedBB.calculateIntercept}: the nearest face crossing of one box, or null. */
    private static @Nullable BlockHitResult intercept(AABB box, Vec3 from, Vec3 to) {
        double rx = to.x - from.x, ry = to.y - from.y, rz = to.z - from.z;
        Vec3 best = null;
        Direction face = Direction.WEST;
        double distance = Double.POSITIVE_INFINITY;
        for (Direction.Axis axis : Direction.Axis.values()) {
            double delta = axis.choose(rx, ry, rz);
            // 1.7 Vec3.getIntermediateWith*Value tests the squared delta against this float.
            if (delta * delta < 1.0E-7F) continue;
            for (Direction.AxisDirection side : Direction.AxisDirection.values()) {
                boolean max = side == Direction.AxisDirection.POSITIVE;
                double t = ((max ? box.max(axis) : box.min(axis)) - axis.choose(from.x, from.y, from.z)) / delta;
                if (t < 0 || t > 1) continue;
                Vec3 point = from.add(rx * t, ry * t, rz * t);
                if (axis != Direction.Axis.X && (point.x < box.minX || point.x > box.maxX)
                        || axis != Direction.Axis.Y && (point.y < box.minY || point.y > box.maxY)
                        || axis != Direction.Axis.Z && (point.z < box.minZ || point.z > box.maxZ)) continue;
                double candidate = from.distanceToSqr(point);
                if (candidate < distance) {
                    best = point;
                    distance = candidate;
                    face = Direction.fromAxisAndDirection(axis, side);
                }
            }
        }
        return best == null ? null : new BlockHitResult(best, face, BlockPos.ZERO, false);
    }
}
