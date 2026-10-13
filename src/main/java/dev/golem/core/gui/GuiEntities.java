package dev.golem.core.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//?} else {
/*import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.gui.GuiGraphics;
*///?}

/**
 * Draws an entity inside a screen, for GUIs that show a 3D preview of something.
 *
 * <p>Vanilla has this only for {@code LivingEntity} ({@code InventoryScreen.renderEntityInInventory}
 * and its 26.1 successor), and every era spells it differently; this is the same routine without the
 * living-entity restriction.
 *
 * <p><b>Why an {@code Entity} and not a model.</b> On 26.1 a screen's transform is a 2D
 * {@code Matrix3x2fStack} and there is no buffer source at extraction time, so 3D inside a GUI goes
 * through a "picture in picture" render state whose renderers are a fixed vanilla list. This routes
 * through {@code GuiEntityRenderer}, which vanilla already registers: it takes an entity render state
 * and dispatches to that entity type's renderer. Drawing a model in a GUI therefore needs an entity
 * that draws it, usually a throwaway instance that is never added to a level.
 *
 * <p>Below 26.1 the screen transform is a {@code PoseStack} and the entity is drawn directly, as
 * {@code renderEntityInInventory} does.
 *
 * <p>{@code translation} is applied <b>before</b> {@code rotation} and is therefore in scaled screen
 * space, where +Y is <em>down</em>. To centre a model that stands on its origin, pass half its height
 * (the value vanilla passes for a player). {@code size} is pixels per block.
 *
 * <p>Client only.
 */
public final class GuiEntities {

    private GuiEntities() {}

    /** Full sky+block light: a GUI preview is lit by the GUI, not by where the entity happens to be. */
    private static final int FULLBRIGHT = 15728880;

    /** Draws {@code entity} centred in the screen rectangle {@code (x0, y0)–(x1, y1)}, GUI pixels. */
    public static void draw(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            Entity entity, int x0, int y0, int x1, int y1,
            float size, Vector3f translation, Quaternionf rotation) {

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();

        //? if >=26.1 {
        EntityRenderer<? super Entity, ?> renderer = dispatcher.getRenderer(entity);
        EntityRenderState state = renderer.createRenderState(entity, 1.0F);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        graphics.entity(state, size, translation, rotation, null, x0, y0, x1, y1);
        //?} else {
        /*graphics.enableScissor(x0, y0, x1, y1);
        graphics.pose().pushPose();
        graphics.pose().translate((x0 + x1) / 2.0F, (y0 + y1) / 2.0F, 50.0F);
        // Negative Z: the GUI's depth axis runs the other way from the world's.
        graphics.pose().scale(size, size, -size);
        graphics.pose().translate(translation.x, translation.y, translation.z);
        graphics.pose().mulPose(rotation);
        Lighting.setupForEntityInInventory();
        dispatcher.setRenderShadow(false);
        dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, graphics.pose(), graphics.bufferSource(), FULLBRIGHT);
        graphics.flush();
        dispatcher.setRenderShadow(true);
        graphics.pose().popPose();
        Lighting.setupFor3DItems();
        graphics.disableScissor();
        *///?}
    }
}
