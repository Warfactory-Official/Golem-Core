package dev.golem.core.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** The camera frustum the level is drawn with this frame, the same on every node. */
public final class Frustums {

    private Frustums() {}

    //? if fabric && <26.1 {
    /*private static volatile @Nullable Frustum captured;
    private static boolean listening;
    *///?}

    /** The frustum of the frame being drawn, or null before there is a level renderer. */
    public static @Nullable Frustum current() {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=26.1 {
        return minecraft.gameRenderer == null ? null : minecraft.gameRenderer.getMainCamera().getCullFrustum();
        //?} elif fabric {
        /*if (!listening) {
            listening = true;
            net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_SETUP.register(
                    context -> captured = context.frustum());
        }
        return captured;
        *///?} else {
        /*return minecraft.levelRenderer == null ? null : minecraft.levelRenderer.getFrustum();
        *///?}
    }

    /** Whether {@code box} (world coordinates) may be seen this frame; true when there is no frustum to ask. */
    public static boolean isVisible(AABB box) {
        Frustum frustum = current();
        return frustum == null || frustum.isVisible(box);
    }
}
