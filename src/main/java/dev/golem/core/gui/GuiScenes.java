package dev.golem.core.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.golem.core.render.CameraInfo;
import dev.golem.core.render.SubmitSink;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.golem.core.render.RecordingSubmitSink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
*///?}

/**
 * Draws a small 3D scene (blocks, block-entity models, items) inside a screen, the way a 1.7
 * {@code GuiScreen} could push a matrix and tesselate the world into itself.
 *
 * <p><b>The frame the scene draws in.</b> The pose handed to {@link Scene#draw} has its origin at the
 * centre of the screen rectangle, one unit per GUI pixel, +X right, +Y <em>down</em> and +Z towards the
 * viewer, the frame a legacy GUI matrix stack had, without the screen-space offset. A scene ported
 * from a 1.7 screen therefore applies the transforms it used there (scale, rotate, translate) unchanged,
 * minus the {@code glTranslated(width / 2, height / 2, z)} that placed it on screen. Depth is available
 * for about ±1000 / guiScale pixels either side of the origin.
 *
 * <p>Geometry goes through a {@link SubmitSink}, so a scene reuses the block and block-entity renderers
 * that already draw into the world: the same {@code GemBER} replay, the same block tesselation.
 *
 * <p>26.1 screens have no 3D transform: a screen's matrix is a 2D {@code Matrix3x2fStack}, and 3D inside
 * a GUI is rendered to an offscreen texture by a "picture in picture" renderer and blitted back. The
 * vanilla renderers of that kind are a fixed list, so this registers one through each loader's own hook
 * (NeoForge's {@code RegisterPictureInPictureRenderersEvent}, Fabric's
 * {@code PictureInPictureRendererRegistry}) and feeds it through the submit pipeline as vanilla's entity
 * preview does. Below 26.1 the GUI matrix stack is a {@code PoseStack} and the scene is drawn straight
 * into it; its depth is then cleared so later screen elements layer over the scene in submission order,
 * as the offscreen blit does.
 *
 * <p>Client only.
 */
public final class GuiScenes {

    private GuiScenes() {}

    /** A 3D scene drawn in the frame described on {@link GuiScenes}. */
    @FunctionalInterface
    public interface Scene {
        void draw(PoseStack pose, SubmitSink sink, CameraInfo camera);
    }

    /**
     * The camera a scene sees: none. A GUI scene is not viewed from a position in the world, so
     * billboarded geometry faces along the scene's own axes.
     */
    static final CameraInfo CAMERA = new CameraInfo(Vec3.ZERO, 0F, new Quaternionf());

    //? if <26.1 {
    /*/^* Where the legacy scene sits in the GUI's depth range; the 1.7 screens used 400. ^/
    private static final float LEGACY_Z = 400F;
    *///?}

    /** Draws {@code scene} into the screen rectangle {@code (x0, y0)–(x1, y1)}, GUI pixels. */
    public static void draw(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            int x0, int y0, int x1, int y1, Scene scene) {
        if (x1 <= x0 || y1 <= y0) return;
        //? if >=26.1 {
        GuiSceneRenderer.submit(graphics, x0, y0, x1, y1, scene);
        //?} else {
        /*Minecraft minecraft = Minecraft.getInstance();
        graphics.flush();
        graphics.enableScissor(x0, y0, x1, y1);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((x0 + x1) / 2.0F, (y0 + y1) / 2.0F, LEGACY_Z);
        Lighting.setupForEntityInInventory();
        RecordingSubmitSink sink = new RecordingSubmitSink();
        scene.draw(pose, sink, CAMERA);
        sink.flush(graphics.bufferSource(), minecraft.level);
        graphics.flush();
        pose.popPose();
        Lighting.setupFor3DItems();
        graphics.disableScissor();
        // 26.1 blits the scene as a flat picture, so whatever the screen draws next lands over it. Here the
        // scene's own depth would still occlude later GUI elements drawn at a lower Z; clearing it gives the
        // same "later submission wins" order.
        RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        *///?}
    }

    /**
     * Registers the scene renderer with the loader. Called once from Golem Core's own entrypoint on a
     * physical client, before the game renderer exists; a no-op below 26.1, which needs no renderer.
     */
    public static void bootstrap() {
        //? if >=26.1 {
        GuiSceneRenderer.register();
        //?}
    }
}
