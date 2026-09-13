package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

public final class GolemFrameContext implements DynamicVisual.Context {

    private static final Matrix4f SCRATCH = new Matrix4f();

    private final Camera camera;
    private final FrustumIntersection frustum;
    private final float partialTick;
    private final int frameId;
    private final Vec3i renderOrigin;
    private final DistanceUpdateLimiter limiter;

    public GolemFrameContext(Camera camera, Matrix4f viewProjection, float partialTick, int frameId, Vec3i renderOrigin, DistanceUpdateLimiter limiter) {
        this.camera = camera;
        this.frustum = new FrustumIntersection(viewProjection);
        this.partialTick = partialTick;
        this.frameId = frameId;
        this.renderOrigin = renderOrigin;
        this.limiter = limiter;
    }

    public static GolemFrameContext create(Camera camera, float partialTick, int frameId, Vec3i renderOrigin, DistanceUpdateLimiter limiter) {
        // Compose ProjMat * cameraRotation with Translate(renderOrigin - cameraPos) so the
        // FrustumIntersection lives in render-origin space — the same space inst.ModelMat
        // translations and visuals' interp dx/dy/dz live in. Without the translate the
        // frustum would be camera-local and rejecting visuals against it would be off by
        // (renderOrigin - cameraPos).
        Vec3 cameraPos = camera.position();
        float dx = (float) (renderOrigin.getX() - cameraPos.x);
        float dy = (float) (renderOrigin.getY() - cameraPos.y);
        float dz = (float) (renderOrigin.getZ() - cameraPos.z);
        Matrix4f mvp = camera.getViewRotationProjectionMatrix(SCRATCH).translate(dx, dy, dz);
        return new GolemFrameContext(camera, mvp, partialTick, frameId, renderOrigin, limiter);
    }

    // Section-aligned (16-block aligned in XYZ) origin near the camera. Per-instance ModelMats
    // are stored relative to this point so the float multiply ModelViewMat * ModelMat * Position
    // operates on small numbers near the camera, dodging the precision blow-up that plain
    // world-space matrices hit at large coordinates. The origin only shifts when the camera
    // crosses a section boundary, so visuals only have to re-rebase on those discrete events.
    public static Vec3i originFor(Camera camera) {
        return SectionPos.of(BlockPos.containing(camera.position())).origin();
    }

    @Override
    public Camera camera() {
        return camera;
    }

    @Override
    public FrustumIntersection frustum() {
        return frustum;
    }

    @Override
    public float partialTick() {
        return partialTick;
    }

    @Override
    public int frameId() {
        return frameId;
    }

    @Override
    public Vec3i renderOrigin() {
        return renderOrigin;
    }

    @Override
    public DistanceUpdateLimiter limiter() {
        return limiter;
    }
}
//?}
