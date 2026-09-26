package dev.golem.core.gui;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Machine-screen widgets: needle gauge, fill bars, button click. */
public final class Gauges {

    /** Needle rasterised at this multiple, pose scales back => smooth rotated edges. */
    private static final int SUPERSAMPLE = 8;

    private Gauges() {}

    /**
     * Needle about {@code (cx, cy)}, swept clockwise from seven o'clock through 270° as {@code progress} 0 -> 1:
     * {@code outline} copy at 1.5x under a {@code color} needle. {@code fill} takes rectangles only => scanline triangle.
     */
    public static void needle(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            int cx, int cy, double progress, double tipLength, double backLength, double backSide, int color,
            int outline) {
        progress = Math.max(0.0, Math.min(1.0, progress));
        GuiDraw.pushMatrix(graphics);
        GuiDraw.translate(graphics, cx, cy);
        GuiDraw.rotate(graphics, (float) Math.toRadians(progress * 270.0 + 45.0));
        GuiDraw.scale(graphics, 1.0F / SUPERSAMPLE, 1.0F / SUPERSAMPLE);
        fillNeedle(graphics, tipLength * 1.5, backLength * 1.5, backSide * 1.5, 0xFF000000 | (outline & 0xFFFFFF));
        fillNeedle(graphics, tipLength, backLength, backSide, 0xFF000000 | (color & 0xFFFFFF));
        GuiDraw.popMatrix(graphics);
    }

    /** {@link #needle} at 5/2/1 proportions, black outline. */
    public static void needle(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            int cx, int cy, double progress, int color) {
        needle(graphics, cx, cy, progress, 5, 2, 1, color, 0);
    }

    private static void fillNeedle(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            double tipLength, double backLength, double backSide, int color) {
        double tip = tipLength * SUPERSAMPLE;
        double back = backLength * SUPERSAMPLE;
        double side = backSide * SUPERSAMPLE;
        double total = tip + back;
        for (int y = (int) Math.round(-back), top = (int) Math.round(tip); y < top; y++) {
            int halfWidth = (int) Math.round(side * (tip - y) / total);
            if (halfWidth > 0) GuiDraw.fill(graphics, -halfWidth, y, halfWidth, y + 1, color);
        }
    }

    /**
     * Bottom {@code value/max} of the {@code width x height} sprite at {@code (u, v)}, drawn bottom-aligned in the box
     * at {@code (x, y)} (energy bars, tanks). 256x256 sheet.
     */
    public static void fillUp(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics, Identifier texture,
            //?} else {
            /*GuiGraphics graphics, ResourceLocation texture,
            *///?}
            int x, int y, int u, int v, int width, int height, long value, long max) {
        int filled = max <= 0 ? 0 : (int) Math.min(height, value * height / max);
        if (filled > 0) GuiDraw.blit(graphics, texture, x, y + height - filled, u, v + height - filled, width, filled, 256, 256);
    }

    /** Left {@code value/max} of the sprite at {@code (u, v)} (progress arrows). 256x256 sheet. */
    public static void fillRight(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics, Identifier texture,
            //?} else {
            /*GuiGraphics graphics, ResourceLocation texture,
            *///?}
            int x, int y, int u, int v, int width, int height, long value, long max) {
        int filled = max <= 0 ? 0 : (int) Math.min(width, value * width / max);
        if (filled > 0) GuiDraw.blit(graphics, texture, x, y, u, v, filled, height, 256, 256);
    }

    /** Vanilla UI click, master channel. */
    public static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
