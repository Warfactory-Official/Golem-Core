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
 * graphics object itself: {@code GuiGraphics} became {@code GuiGraphicsExtractor} once HUD drawing
 * split into an extract pass.
 *
 * <p>Neither is a text replacement: the pipeline argument has to be added or removed, not renamed. Screens
 * call these methods and are written once.
 */
public final class GuiDraw {

    //? if >=26.1 {
    private static final com.mojang.blaze3d.pipeline.RenderPipeline GUI_ADDITIVE =
            com.mojang.blaze3d.pipeline.RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(dev.golem.core.Ids.of("golemcore", "pipeline/gui_additive"))
                    .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(
                            com.mojang.blaze3d.pipeline.BlendFunction.LIGHTNING)).build();
    //?}

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

    /** Draws a destination rectangle from an arbitrary normalized source rectangle. */
    public static void blitUv(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics, Identifier texture,
            //?} else {
            /*GuiGraphics graphics, ResourceLocation texture,
            *///?}
            int x, int y, int width, int height, float u0, float v0, float u1, float v1,
            int textureWidth, int textureHeight) {
        blitUv(graphics, texture, x, y, width, height, u0, v0, u1, v1, textureWidth, textureHeight, false);
    }

    /** Additive HUD lights retain their black background in the atlas; black contributes no light. */
    public static void blitUv(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics, Identifier texture,
            //?} else {
            /*GuiGraphics graphics, ResourceLocation texture,
            *///?}
            int x, int y, int width, int height, float u0, float v0, float u1, float v1,
            int textureWidth, int textureHeight, boolean additive) {
        int sourceWidth = Math.max(1, Math.round((u1 - u0) * textureWidth));
        int sourceHeight = Math.max(1, Math.round((v1 - v0) * textureHeight));
        //? if >=26.1 {
        graphics.blit(additive ? GUI_ADDITIVE : RenderPipelines.GUI_TEXTURED, texture, x, y, u0 * textureWidth, v0 * textureHeight,
                width, height, sourceWidth, sourceHeight, textureWidth, textureHeight);
        //?} else {
        /*com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        if (additive) com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        graphics.blit(texture, x, y, width, height, u0 * textureWidth, v0 * textureHeight, sourceWidth, sourceHeight,
                textureWidth, textureHeight);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        *///?}
    }

    /**
     * Stretches a normalised source rectangle of a texture across the whole GUI.
     *
     * <p>Vanilla names a blit's source region in texels, and below 26.1 no overload takes UVs. This draws the
     * whole texture into an oversized rectangle and lets the screen edge crop it, which needs no texture
     * dimensions and behaves the same on every version.
     */
    public static void blitUvFullscreen(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics, Identifier texture,
            //?} else {
            /*GuiGraphics graphics, ResourceLocation texture,
            *///?}
            float u0, float v0, float u1, float v1) {
        float uSpan = u1 - u0;
        float vSpan = v1 - v0;
        if (uSpan <= 0F || vSpan <= 0F) return;
        int width = Math.round(graphics.guiWidth() / uSpan);
        int height = Math.round(graphics.guiHeight() / vSpan);
        blitUv(graphics, texture, Math.round(-u0 * width), Math.round(-v0 * height), width, height,
                0F, 0F, 1F, 1F, 1, 1);
    }

    /**
     * Draws a stitched atlas sprite into a GUI rectangle, including a generated one.
     *
     * <p>A GUI blit names a texture file; a sprite that only exists on an atlas has an atlas and a corner of
     * it. The atlas's pixel size is not public on every version, but the sprite's size over its UV span
     * gives it.
     */
    public static void blitSprite(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics, dev.golem.core.render.Sprites.Atlas atlas, Identifier sprite,
            //?} else {
            /*GuiGraphics graphics, dev.golem.core.render.Sprites.Atlas atlas, ResourceLocation sprite,
            *///?}
            int x, int y, int width, int height) {
        var stitched = dev.golem.core.render.Sprites.sprite(atlas, sprite);
        float u0 = stitched.getU0();
        float v0 = stitched.getV0();
        float u1 = stitched.getU1();
        float v1 = stitched.getV1();
        if (u1 <= u0 || v1 <= v0) return;
        blitUv(graphics, stitched.atlasLocation(), x, y, width, height, u0, v0, u1, v1,
                Math.round(stitched.contents().width() / (u1 - u0)),
                Math.round(stitched.contents().height() / (v1 - v0)));
    }

    /** Draws a crosshair with vanilla's inverse-colour blend against the pixels behind it. */
    public static void crosshair(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics, Identifier texture,
            //?} else {
            /*GuiGraphics graphics, ResourceLocation texture,
            *///?}
            int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        //? if >=26.1 {
        graphics.blit(RenderPipelines.CROSSHAIR, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        //?} else {
        /*com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        *///?}
    }

    /**
     * Fills an axis-aligned rectangle with a solid ARGB colour, from {@code (x0, y0)} inclusive to
     * {@code (x1, y1)} exclusive.
     *
     * <p>Include the alpha: {@code 0xFFFFFF} has alpha zero and draws nothing.
     */
    public static void fill(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y1, color);
    }

    /**
     * Fills a rectangle whose colour runs from {@code colorTop} at {@code y0} to {@code colorBottom} at
     * {@code y1}, each channel interpolated, alpha included, which is what makes it useful for a fade.
     * Same name and argument order on every version; only the graphics type differs.
     */
    public static void fillGradient(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            int x0, int y0, int x1, int y1, int colorTop, int colorBottom) {
        graphics.fillGradient(x0, y0, x1, y1, colorTop, colorBottom);
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
        // Two things this era needs that 26.1 does itself; both silently give "no translucency":
        //
        // 1. Blending is off. The plain blit path (getPositionTexShader) never calls enableBlend, so a
        //    shader-colour alpha is discarded and the quad lands opaque. defaultBlendFunc is set too,
        //    since the ambient func is not guaranteed mid-GUI-pass.
        //
        // 2. Z-ordering is by depth buffer, not submission order. renderItem raises the item to Z ~150
        //    while blit draws at Z 0, so a tint quad fails the depth test against the item it should
        //    dim. Raising the transform above the item reproduces 26.1's "later submission wins".
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
     * <p>A helper rather than a Stonecutter replacement: {@code extractTooltip} (the
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

    /**
     * The text colour rule of 1.7's {@code FontRenderer} and of {@code Font} before 1.21.6: a colour whose top six
     * bits are clear ({@code 0x404040}, {@code 0xFFFFFF}, ...) is drawn opaque. 26.1 takes the int as plain ARGB and
     * skips any text with alpha 0, so the same call drew on 1.20.1/1.21.1 and was invisible on 26.1. Applied by every
     * text path here (and by the world-text sinks) so a colour means the same on all versions.
     */
    public static int textColor(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }

    /** Draws a string. 26.1 shortened {@code drawString} to {@code text}. */
    public static void text(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            net.minecraft.client.gui.Font font, String value, int x, int y, int color, boolean shadow) {
        color = textColor(color);
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
        color = textColor(color);
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

    /** Draws an item stack's count and durability bar over it. 26.1 renamed {@code renderItemDecorations}. */
    public static void itemDecorations(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            net.minecraft.client.gui.Font font, net.minecraft.world.item.ItemStack stack, int x, int y) {
        //? if >=26.1 {
        graphics.itemDecorations(font, stack, x, y);
        //?} else {
        /*graphics.renderItemDecorations(font, stack, x, y);
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
