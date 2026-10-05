package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.golem.core.world.SubLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/**
 * Drawing in a world-space pass (outlines, ghosts, overlays) at a block that may sit on a moving sub-level
 * ({@link SubLevels}). Such a block lives at block-grid coordinates far from where the structure is drawn, so
 * {@code pos - camera} puts the drawing millions of blocks away.
 *
 * <p>{@link #around} prepares a camera-relative pose for drawing around one anchor block: afterwards every vertex
 * is given relative to the anchor ({@code pos - anchor}), and lands where the block is seen, turned with the
 * sub-level. Off a sub-level it is exactly {@code translate(anchor - camera)}, so callers need no second path.
 * The large terms are combined in doubles before anything reaches the float pose, so precision is the same as
 * for a block in the world.
 *
 * <p>Client only.
 */
public final class SubLevelPoses {

    private SubLevelPoses() {}

    public static void around(PoseStack pose, Level level, BlockPos anchor, Vec3 camera, float partialTick) {
        around(pose, level, anchor.getX(), anchor.getY(), anchor.getZ(), camera, partialTick);
    }

    public static void around(PoseStack pose, Level level, double ax, double ay, double az, Vec3 camera,
                              float partialTick) {
        // The point a little inside the anchor decides which sub-level it is on, as for the block itself.
        SubLevels.RenderPose sub = SubLevels.renderPose(level, ax + 0.5, ay + 0.5, az + 0.5, partialTick);
        if (sub == null) {
            pose.translate(ax - camera.x, ay - camera.y, az - camera.z);
            return;
        }
        // world(anchor + d) = R * (S * (anchor - rotationPoint + d)) + position
        Vector3d anchorInWorld = new Vector3d(
                (ax - sub.rotationPoint().x) * sub.scale().x,
                (ay - sub.rotationPoint().y) * sub.scale().y,
                (az - sub.rotationPoint().z) * sub.scale().z);
        sub.orientation().transform(anchorInWorld);
        pose.translate(anchorInWorld.x + sub.position().x - camera.x,
                anchorInWorld.y + sub.position().y - camera.y,
                anchorInWorld.z + sub.position().z - camera.z);
        pose.mulPose(new Quaternionf(sub.orientation()));
        pose.scale((float) sub.scale().x, (float) sub.scale().y, (float) sub.scale().z);
    }
}
