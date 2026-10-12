package dev.golem.core.viewer;

import dev.golem.core.gui.GuiDraw;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.world.item.ItemStack;

/**
 * Something a recipe category draws: a texture region, an animated progress bar over one, an item, or the viewer's
 * standard slot frame. JEI's {@code IDrawable} family ({@code IGuiHelper.createDrawable}, {@code createAnimatedDrawable},
 * {@code getSlotDrawable}, {@code createDrawableItemStack}) in a form both viewers can use, so a category is written once.
 *
 * <p>The texture and animation drawing is JEI 29's own arithmetic, so a category draws the same pixels in JEI and in REI:
 * a region is one {@code blit} of {@code (u + maskLeft, v + maskTop)} at {@code (x + maskLeft, y + maskTop)}, and an
 * animated region masks {@code floorDiv(elapsed % cycle * (max + 1), cycle)} pixels (counted down unless inverted) from
 * the side opposite its start, timed from its creation like JEI's {@code TickTimer}.
 */
public interface Drawable {

    int width();

    int height();

    void draw(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            int x, int y);

    /** The side an animated drawable starts filling from (JEI's {@code IDrawableAnimated.StartDirection}). */
    enum Direction {
        TOP, BOTTOM, LEFT, RIGHT;

        Direction opposite() {
            return switch (this) {
                case TOP -> BOTTOM;
                case BOTTOM -> TOP;
                case LEFT -> RIGHT;
                case RIGHT -> LEFT;
            };
        }
    }

    /** A {@code width x height} region at {@code (u, v)} of a 256x256 texture. */
    static Texture texture(
            //? if >=26.1 {
            Identifier texture,
            //?} else {
            /*ResourceLocation texture,
            *///?}
            int u, int v, int width, int height) {
        return new Texture(texture, u, v, width, height, 256, 256);
    }

    /** A region of a texture of the given size. */
    static Texture texture(
            //? if >=26.1 {
            Identifier texture,
            //?} else {
            /*ResourceLocation texture,
            *///?}
            int u, int v, int width, int height, int textureWidth, int textureHeight) {
        return new Texture(texture, u, v, width, height, textureWidth, textureHeight);
    }

    /**
     * A region revealed over {@code ticksPerCycle} ticks, starting from {@code start} (JEI's
     * {@code createAnimatedDrawable}: inverted runs the other way, from the opposite side).
     */
    static Drawable animated(Texture region, int ticksPerCycle, Direction start, boolean inverted) {
        return new Animated(region, ticksPerCycle, start, inverted);
    }

    /** The viewer's standard 18x18 slot frame, drawn one pixel up and left of a slot's item. */
    static Drawable slot() {
        return StandardSlot.INSTANCE;
    }

    /** An item, 16x16. */
    static Drawable item(ItemStack stack) {
        return new Item(stack);
    }

    /** A texture region (JEI's {@code DrawableResource} with no padding). */
    final class Texture implements Drawable {
        //? if >=26.1 {
        private final Identifier texture;
        //?} else {
        /*private final ResourceLocation texture;
        *///?}
        private final int u, v, width, height, textureWidth, textureHeight;

        Texture(
                //? if >=26.1 {
                Identifier texture,
                //?} else {
                /*ResourceLocation texture,
                *///?}
                int u, int v, int width, int height, int textureWidth, int textureHeight) {
            this.texture = texture;
            this.u = u;
            this.v = v;
            this.width = width;
            this.height = height;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public void draw(
                //? if >=26.1 {
                GuiGraphicsExtractor graphics,
                //?} else {
                /*GuiGraphics graphics,
                *///?}
                int x, int y) {
            draw(graphics, x, y, 0, 0, 0, 0);
        }

        /** Draws the region with the given number of pixels cut from each side. */
        public void draw(
                //? if >=26.1 {
                GuiGraphicsExtractor graphics,
                //?} else {
                /*GuiGraphics graphics,
                *///?}
                int x, int y, int maskTop, int maskBottom, int maskLeft, int maskRight) {
            int w = width - maskRight - maskLeft;
            int h = height - maskBottom - maskTop;
            if (w <= 0 || h <= 0) return;
            GuiDraw.blit(graphics, texture, x + maskLeft, y + maskTop, u + maskLeft, v + maskTop, w, h,
                    textureWidth, textureHeight);
        }
    }

    /** A texture region masked by a tick timer (JEI's {@code DrawableAnimated} over a {@code TickTimer}). */
    final class Animated implements Drawable {
        private final Texture region;
        private final Direction direction;
        private final int msPerCycle;
        private final int maxValue;
        private final boolean countDown;
        private final long startTime = System.currentTimeMillis();

        Animated(Texture region, int ticksPerCycle, Direction start, boolean inverted) {
            if (ticksPerCycle <= 0) throw new IllegalArgumentException("Must have at least 1 tick per cycle.");
            this.region = region;
            this.direction = inverted ? start.opposite() : start;
            this.msPerCycle = ticksPerCycle * 50;
            this.maxValue = direction == Direction.TOP || direction == Direction.BOTTOM ? region.height() : region.width();
            this.countDown = !inverted;
        }

        @Override
        public int width() {
            return region.width();
        }

        @Override
        public int height() {
            return region.height();
        }

        private int value() {
            long elapsed = (System.currentTimeMillis() - startTime) % msPerCycle;
            int value = (int) Math.floorDiv(elapsed * (maxValue + 1), msPerCycle);
            return countDown ? maxValue - value : value;
        }

        @Override
        public void draw(
                //? if >=26.1 {
                GuiGraphicsExtractor graphics,
                //?} else {
                /*GuiGraphics graphics,
                *///?}
                int x, int y) {
            int value = value();
            int maskTop = 0, maskBottom = 0, maskLeft = 0, maskRight = 0;
            switch (direction) {
                case TOP -> maskBottom = value;
                case BOTTOM -> maskTop = value;
                case LEFT -> maskRight = value;
                case RIGHT -> maskLeft = value;
            }
            region.draw(graphics, x, y, maskTop, maskBottom, maskLeft, maskRight);
        }
    }

    /**
     * The standard slot frame. Each viewer draws its own (JEI's slot sprite, REI's slot background); this fallback, used
     * only when a category draws it by hand, is vanilla's inventory slot.
     */
    final class StandardSlot implements Drawable {
        static final StandardSlot INSTANCE = new StandardSlot();

        private StandardSlot() {}

        @Override
        public int width() {
            return 18;
        }

        @Override
        public int height() {
            return 18;
        }

        @Override
        public void draw(
                //? if >=26.1 {
                GuiGraphicsExtractor graphics,
                //?} else {
                /*GuiGraphics graphics,
                *///?}
                int x, int y) {
            GuiDraw.fill(graphics, x, y, x + 17, y + 1, 0xFF373737);
            GuiDraw.fill(graphics, x, y + 1, x + 1, y + 17, 0xFF373737);
            GuiDraw.fill(graphics, x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
            GuiDraw.fill(graphics, x + 17, y + 1, x + 18, y + 17, 0xFFFFFFFF);
            GuiDraw.fill(graphics, x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        }
    }

    /** An item stack at GUI scale. */
    final class Item implements Drawable {
        private final ItemStack stack;

        Item(ItemStack stack) {
            this.stack = stack;
        }

        public ItemStack stack() {
            return stack;
        }

        @Override
        public int width() {
            return 16;
        }

        @Override
        public int height() {
            return 16;
        }

        @Override
        public void draw(
                //? if >=26.1 {
                GuiGraphicsExtractor graphics,
                //?} else {
                /*GuiGraphics graphics,
                *///?}
                int x, int y) {
            GuiDraw.item(graphics, stack, x, y);
        }
    }
}
