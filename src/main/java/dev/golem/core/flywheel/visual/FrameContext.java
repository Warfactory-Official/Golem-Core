package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

import net.minecraft.client.Camera;
import net.minecraft.core.Vec3i;
import org.joml.FrustumIntersection;

public interface FrameContext {

    Camera camera();

    FrustumIntersection frustum();

    float partialTick();

    int frameId();

    Vec3i renderOrigin();

    DistanceUpdateLimiter limiter();
}
//?}
