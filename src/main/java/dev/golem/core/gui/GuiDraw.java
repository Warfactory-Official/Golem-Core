package dev.golem.core.gui;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Screen drawing operations whose call shape changed across versions.
 *
 * <p>26.1 made the render pipeline an explicit first argument to {@code blit}
 * ({@code RenderPipelines.GUI_TEXTURED}), where older versions inferred it. It also renamed the
 * graphics object itself — {@code GuiGraphics} became {@code GuiGraphicsExtractor} once HUD drawing
 * split into an extract pass.
 *
 * <p>Neither difference is expressible as a text replacement: the pipeline argument has to be added or
 * removed, not renamed. Routing blits through here means screens are written once and the argument
 * juggling lives in a single method.
 */
public final class GuiDraw {

    private GuiDraw() {}

    /**
     * Draws a sub-rectangle of a texture. Argument order matches 26.1's {@code blit} minus the
     * pipeline: destination x/y, source u/v, width/height, then the texture's full dimensions.
     */
    public static void blit(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics, Identifier texture,
            //?} else {
            /*GuiGraphics graphics, ResourceLocation texture,
            *///?}
            int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        //? if >=26.1 {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        //?} else {
        /*graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        *///?}
    }

    /**
     * Draws a tinted sub-rectangle. 26.1 takes the ARGB tint as a trailing {@code blit} argument;
     * older versions have no such parameter and must set, draw, then reset the shader colour.
     */
    public static void blit(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics, Identifier texture,
            //?} else {
            /*GuiGraphics graphics, ResourceLocation texture,
            *///?}
            int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight,
            int color) {
        //? if >=26.1 {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight, color);
        //?} else {
        /*float a = ((color >> 24) & 0xFF) / 255F;
        float r = ((color >> 16) & 0xFF) / 255F;
        float g = ((color >> 8) & 0xFF) / 255F;
        float b = (color & 0xFF) / 255F;
        // Two things this era needs that 26.1 does for itself, both of which silently produce "no
        // translucency at all" rather than a wrong shade:
        //
        // 1. Blending is off. The plain blit path draws with getPositionTexShader and never calls
        //    enableBlend, so a shader-colour alpha is discarded and the quad lands opaque. 26.1's
        //    coloured blit enables blend around its draw. defaultBlendFunc is set too: vanilla's
        //    coloured path relies on the ambient func, which is not guaranteed mid-GUI-pass.
        //
        // 2. Z-ordering is by depth buffer, not submission order. 26.1 draws GUI elements in the order
        //    they are submitted, so a tint quad drawn after an item lands on top of it. Here the item is
        //    rendered at a raised Z (renderItem pushes it to ~150) while blit draws at Z 0, so the quad
        //    fails the depth test against the item it is meant to dim and never appears. Raising the
        //    transform above the item reproduces 26.1's "later submission wins".
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        graphics.setColor(r, g, b, a == 0F ? 1F : a);
        graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        graphics.setColor(1F, 1F, 1F, 1F);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        graphics.pose().popPose();
        *///?}
    }

    /**
     * Queues a tooltip for this frame. 26.1 renamed {@code renderTooltip} to
     * {@code setTooltipForNextFrame} when HUD drawing became a deferred extract pass; the parameters
     * are otherwise identical.
     *
     * <p>Deliberately a helper rather than a Stonecutter replacement: {@code extractTooltip} (the
     * Screen override) also maps to {@code renderTooltip}, so a string swap would be non-injective
     * and would corrupt the source on the reverse pass.
     */
    public static void tooltip(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            net.minecraft.client.gui.Font font,
            java.util.List<net.minecraft.network.chat.Component> lines,
            java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> image,
            int mouseX, int mouseY) {
        //? if >=26.1 {
        graphics.setTooltipForNextFrame(font, lines, image, mouseX, mouseY);
        //?} else {
        /*graphics.renderTooltip(font, lines, image, mouseX, mouseY);
        *///?}
    }

    /**
     * Starts a new drawing stratum: everything submitted after this lands above everything before it.
     *
     * <p>26.1 tracks GUI layering explicitly, because its renderer draws elements in submission order
     * rather than by depth. Older versions have no such concept and resolve overlap with the depth
     * buffer, so the equivalent is to raise Z. The step matches the gap vanilla leaves between an item
     * and its overlays, which is what this is used for.
     */
    public static void nextStratum(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics
            //?} else {
            /*GuiGraphics graphics
            *///?}
            ) {
        //? if >=26.1 {
        graphics.nextStratum();
        //?} else {
        /*graphics.pose().translate(0.0F, 0.0F, 200.0F);
        *///?}
    }

    /** Draws a string. 26.1 shortened {@code drawString} to {@code text}. */
    public static void text(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            net.minecraft.client.gui.Font font, String value, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        graphics.text(font, value, x, y, color, shadow);
        //?} else {
        /*graphics.drawString(font, value, x, y, color, shadow);
        *///?}
    }

    /** Draws a formatted component. */
    public static void text(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component value,
            int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        graphics.text(font, value, x, y, color, shadow);
        //?} else {
        /*graphics.drawString(font, value, x, y, color, shadow);
        *///?}
    }

    /** Draws an item stack at GUI scale. 26.1 shortened {@code renderItem} to {@code item}. */
    public static void item(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            net.minecraft.world.item.ItemStack stack, int x, int y) {
        //? if >=26.1 {
        graphics.item(stack, x, y);
        //?} else {
        /*graphics.renderItem(stack, x, y);
        *///?}
    }

    /**
     * Saves the GUI transform. 26.1 exposes push/pop directly on the graphics object; older versions
     * reach through to the underlying {@code PoseStack}.
     */
    public static void pushMatrix(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics
            //?} else {
            /*GuiGraphics graphics
            *///?}
            ) {
        //? if >=26.1 {
        graphics.pose().pushMatrix();
        //?} else {
        /*graphics.pose().pushPose();
        *///?}
    }

    /** Restores the transform saved by {@link #pushMatrix}. */
    public static void popMatrix(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics
            //?} else {
            /*GuiGraphics graphics
            *///?}
            ) {
        //? if >=26.1 {
        graphics.pose().popMatrix();
        //?} else {
        /*graphics.pose().popPose();
        *///?}
    }

    /**
     * Translates the GUI transform.
     *
     * <p>26.1's GUI stack is a 2D {@code Matrix3x2fStack}; before that it was the same 3D
     * {@code PoseStack} used for world rendering, so the older call needs a zero Z.
     */
    public static void translate(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            float x, float y) {
        //? if >=26.1 {
        graphics.pose().translate(x, y);
        //?} else {
        /*graphics.pose().translate(x, y, 0F);
        *///?}
    }

    /** Scales the GUI transform. */
    public static void scale(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            float x, float y) {
        //? if >=26.1 {
        graphics.pose().scale(x, y);
        //?} else {
        /*graphics.pose().scale(x, y, 1F);
        *///?}
    }

    /** Rotates the GUI transform about the screen normal, in radians. */
    public static void rotate(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            float radians) {
        //? if >=26.1 {
        graphics.pose().rotate(radians);
        //?} else {
        /*graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(radians));
        *///?}
    }
}
