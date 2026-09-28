package dev.golem.core.gui;

/**
 * A scissor rectangle nested inside whatever scissor is already active, and put back afterwards - below 26.1, where a
 * custom item renderer draws straight to GL and can only clip itself with {@code RenderSystem.enableScissor}.
 *
 * <p>That call <em>replaces</em> the active rectangle and {@code disableScissor} turns clipping off altogether. Inside a
 * screen that is itself scissored - the advancement window clips its tree to 234x113 - one clipped icon therefore
 * widened the clip to its slot and then removed it, so every icon drawn after it spilled out over the window frame.
 * {@link #push} intersects with the rectangle in force (read back from GL) and {@link #pop} restores it exactly.
 * 26.1 gives every GUI item its own bounds in the render state, so there this is never needed and does nothing.
 *
 * <p>Client, render thread.
 */
public final class Scissors {

    private Scissors() {}

    //? if <26.1 {
    /*private static final java.util.ArrayDeque<int[]> SAVED = new java.util.ArrayDeque<>();
    private static final int[] NONE = new int[0];
    *///?}

    /** Clips to the framebuffer rectangle (origin bottom-left) within the current clip; false if nothing is set. */
    public static boolean push(int x, int y, int width, int height) {
        //? if <26.1 {
        /*boolean enabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        int[] box = new int[4];
        if (enabled) org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, box);
        SAVED.push(enabled ? box : NONE); // ArrayDeque refuses null: NONE marks "no clip was active"
        int x0 = x, y0 = y, x1 = x + width, y1 = y + height;
        if (enabled) {
            x0 = Math.max(x0, box[0]);
            y0 = Math.max(y0, box[1]);
            x1 = Math.min(x1, box[0] + box[2]);
            y1 = Math.min(y1, box[1] + box[3]);
        }
        com.mojang.blaze3d.systems.RenderSystem.enableScissor(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
        return true;
        *///?} else {
        return false;
        //?}
    }

    /** Restores the clip {@link #push} found. */
    public static void pop() {
        //? if <26.1 {
        /*if (SAVED.isEmpty()) {
            com.mojang.blaze3d.systems.RenderSystem.disableScissor();
            return;
        }
        int[] box = SAVED.pop();
        if (box == NONE) com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        else com.mojang.blaze3d.systems.RenderSystem.enableScissor(box[0], box[1], box[2], box[3]);
        *///?}
    }
}
