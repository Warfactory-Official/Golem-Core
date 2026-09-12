package dev.golem.core.render.display;

import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

/**
 * The frames 1.7's item renderers wrapped around a custom render body, one matrix per display slot.
 *
 * <p>A ported item icon is two halves. The <b>body</b> is the mod's own {@code renderInventory} /
 * {@code renderCommon} calls, transcribed verbatim wherever that item is drawn. The <b>frame</b> is what
 * the engine applied around it — {@code ItemRenderBase.renderItem}'s per-type preamble, Forge's
 * else-pose, and the transform from 1.7's Y-down 16-pixel slot space into modern block space. The body
 * belongs to the item; the frame is shared by every item on the same 1.7 render class, and is what this
 * class holds.
 *
 * <p>The numbers are CE 1.12.2's, from {@code com/hbm/render/item/ItemRenderBase.java} and
 * {@code ItemRenderBaseLegacy.java}, op for op. They are not a derivation and not a fit: CE already did
 * the 1.7 → modern conversion once, and an independent port (NEXT, on 26.2) composes the same values
 * from the 1.7 call chain.
 *
 * <p>Compose a pose by post-multiplying the body onto the frame — {@code frame(klass, slot).mul(body)} —
 * and hand the result to {@link DisplayFrame}, which turns it into the triple vanilla stores. Nothing
 * here mutates: every call builds a fresh matrix.
 */
public final class Pose17 {

    private Pose17() {}

    /**
     * Which 1.7 render class an item's frames come from.
     *
     * <p>The distinction is not cosmetic. {@link #ITEM_RENDER_BASE} is the 1.7-exact set; CE's own
     * comment on {@link #ITEM_RENDER_BASE_LEGACY} says it is "reserved for items that does not have a 1.7
     * counterpart to copy transform from", so its numbers are invented framing for new content. Putting a
     * 1.7 item on the Legacy frames moves its icon about 13 GUI pixels up a 16-pixel slot.
     */
    public enum Klass {

        /** {@code ItemRenderBase}: what 1.7 applied, for an item that existed in 1.7. */
        ITEM_RENDER_BASE,

        /**
         * {@code ItemRenderBaseLegacy}: CE's framing for content with no 1.7 original.
         *
         * <p>Incomplete on its own for an item that is not an {@code ItemBlock} or an {@code ItemArmor}:
         * CE also has those bind {@code BakedModelTransforms.defaultItemTransforms()}, so the model's own
         * display block composes on top of the frame. Every block item takes the identity there, which is
         * why these matrices are the whole story for a machine and are not for a handheld.
         */
        ITEM_RENDER_BASE_LEGACY
    }

    /** The frame one render class applies in one display slot. */
    public static Matrix4f frame(Klass klass, DisplaySlot slot) {
        return switch (klass) {
            case ITEM_RENDER_BASE -> itemRenderBase(slot);
            case ITEM_RENDER_BASE_LEGACY -> itemRenderBaseLegacy(slot);
        };
    }

    /** Every slot's frame for one render class. */
    public static Map<DisplaySlot, Matrix4f> frames(Klass klass) {
        Map<DisplaySlot, Matrix4f> frames = new EnumMap<>(DisplaySlot.class);
        for (DisplaySlot slot : DisplaySlot.values()) {
            frames.put(slot, frame(klass, slot));
        }
        return frames;
    }

    /**
     * The 1.7-exact frames. GUI is 1.7's {@code translate(8, 10, 0); rotate(-30, X); rotate(45, Y);
     * scale(-1, -1, -1)} with the pixel translate folded into block units and the Y flip folded into the
     * 225 degree turn; the hand and head chains end in Forge's else-pose followed by the 1.7 equipped
     * preamble.
     */
    private static Matrix4f itemRenderBase(DisplaySlot slot) {
        return switch (slot) {
            case FIRST_PERSON_RIGHT_HAND -> new Matrix4f()
                    .translate(0.5F, 0.5F, 0.5F).rotateY(rad(45)).scale(0.4F)
                    .translate(0F, -0.3F, 0F).scale(1.5F).rotateY(rad(50)).rotateZ(rad(335))
                    .translate(-0.9375F, -0.0625F, 0F)
                    .translate(0.5F, 0.25F, 0F).scale(0.25F).rotateY(rad(90));
            case FIRST_PERSON_LEFT_HAND -> new Matrix4f()
                    .translate(0.5F, 0.5F, 0.5F).rotateY(rad(-45)).scale(0.4F)
                    .translate(0F, -0.3F, 0F).scale(1.5F).rotateY(rad(-50)).rotateZ(rad(-335))
                    .translate(0.9375F, -0.0625F, 0F)
                    .translate(-0.5F, 0.25F, 0F).scale(0.25F).rotateY(rad(-90));
            case THIRD_PERSON_RIGHT_HAND -> new Matrix4f()
                    .translate(0.4375F, 0.375F, 1.125F).rotateY(rad(180)).rotateX(rad(90))
                    .translate(0.1875F, 0.625F, -0.125F).scale(0.375F)
                    .rotateZ(rad(60)).rotateX(rad(-90)).rotateZ(rad(20))
                    .translate(0F, -0.3F, 0F).scale(1.5F).rotateY(rad(50)).rotateZ(rad(335))
                    .translate(-0.9375F, -0.0625F, 0F)
                    .translate(0.5F, 0.25F, 0F).scale(0.25F);
            case THIRD_PERSON_LEFT_HAND -> new Matrix4f()
                    .translate(0.5625F, 0.375F, 1.125F).rotateY(rad(180)).rotateX(rad(90))
                    .translate(-0.1875F, 0.625F, -0.125F).scale(0.375F)
                    .rotateZ(rad(-60)).rotateX(rad(-90)).rotateZ(rad(-20))
                    .translate(0F, -0.3F, 0F).scale(1.5F).rotateY(rad(-50)).rotateZ(rad(-335))
                    .translate(0.9375F, -0.0625F, 0F)
                    .translate(-0.5F, 0.25F, 0F).scale(0.25F);
            case HEAD -> new Matrix4f()
                    .translate(0.5F, 0.5F, 0.5F).scale(1.6F, -1.6F, -1.6F).rotateY(rad(180))
                    .translate(0F, 0.25F, 0F)
                    .translate(0F, -0.3F, 0F).scale(1.5F).rotateY(rad(50)).rotateZ(rad(335))
                    .translate(-0.9375F, -0.0625F, 0F)
                    .translate(0.5F, 0.25F, 0F).scale(0.25F);
            case GUI -> new Matrix4f()
                    .translate(0.5F, 0.375F, 0F).rotateX(rad(30)).rotateY(rad(225)).scale(0.0625F);
            case GROUND -> new Matrix4f()
                    .translate(0.5F, 0.25F, 0.5F).scale(0.1875F).rotateY(rad(90));
            case FIXED -> new Matrix4f()
                    .translate(0.5F, 0.34F, 0.53125F).rotateY(rad(-90)).scale(0.375F);
        };
    }

    /**
     * CE's invented frames for post-1.7 content. HEAD reuses the third-person right-hand matrix, which is
     * CE's own mapping rather than a separate pose.
     */
    private static Matrix4f itemRenderBaseLegacy(DisplaySlot slot) {
        return switch (slot) {
            case FIRST_PERSON_RIGHT_HAND -> new Matrix4f()
                    .translate(0.5F, 0F, 0.5F).translate(0F, 0.3F, 0F).scale(0.2F).rotateY(rad(135));
            case FIRST_PERSON_LEFT_HAND -> new Matrix4f()
                    .translate(0.5F, 0F, 0.5F).translate(0F, 0.3F, 0F).scale(0.2F).rotateY(rad(45));
            case THIRD_PERSON_RIGHT_HAND, HEAD -> new Matrix4f()
                    .translate(0.5F, 0F, 0.5F).translate(0F, 0.25F, 0F).scale(0.1875F).rotateY(rad(180));
            case THIRD_PERSON_LEFT_HAND -> new Matrix4f()
                    .translate(0.5F, 0F, 0.5F).translate(0F, 0.25F, 0F).scale(0.1875F);
            case GROUND -> new Matrix4f()
                    .translate(0.5F, 0F, 0.5F).translate(0F, 0.3F, 0F).scale(0.125F).rotateY(rad(90));
            case FIXED -> new Matrix4f()
                    .translate(0.5F, 0F, 0.5F).translate(0F, 0.3F, 0F).scale(0.25F).rotateY(rad(90));
            case GUI -> new Matrix4f()
                    .rotateX(rad(30)).rotateY(rad(225)).scale(0.062F).translate(0F, 11.3F, -11.3F);
        };
    }

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
