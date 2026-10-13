package dev.golem.core.render.display;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemDisplayFrameTest {

    /** Where the model's own origin lands in slot space, which is what the engine finally applies. */
    private static Vector3f originUnder(Matrix4f frame) {
        return new Matrix4f().translation(-0.5F, -0.5F, -0.5F).mul(frame)
                .transformPosition(new Vector3f());
    }

    private static Vector3f originShippedAs(DisplayFrame frame) {
        return frame.applied().translate(-0.5F, -0.5F, -0.5F).transformPosition(new Vector3f());
    }

    @Test
    void everyFrameSurvivesTheTripVanillaWillTakeItOn() {
        for (Pose17.Klass klass : Pose17.Klass.values()) {
            Map<DisplaySlot, DisplayFrame> frames = DisplayFrame.of(klass.name(), Pose17.frames(klass));

            assertEquals(DisplaySlot.values().length, frames.size());
            for (DisplayFrame frame : frames.values()) {
                assertTrue(frame.residual() <= DisplayFrame.MAX_RESIDUAL,
                        klass + " " + frame.slot() + " misses by " + frame.residual());
            }
        }
    }

    /**
     * 1.7 drew an inventory item at {@code translate(8, 10, 0)} in a 16-pixel slot whose y ran downward,
     * which is two pixels below the middle. The frame has to put the model's origin in the same place.
     */
    @Test
    void theGuiFrameLandsWhereSeventeenPutTheOrigin() {
        Vector3f origin = originUnder(Pose17.frame(Pose17.Klass.ITEM_RENDER_BASE, DisplaySlot.GUI));

        assertEquals(0F, origin.x, 1.0E-6F);
        assertEquals(-2F / 16F, origin.y, 1.0E-6F);
    }

    @Test
    void theShippedTripleAgreesWithTheFrameItCameFrom() {
        for (Pose17.Klass klass : Pose17.Klass.values()) {
            Map<DisplaySlot, Matrix4f> poses = Pose17.frames(klass);
            Map<DisplaySlot, DisplayFrame> frames = DisplayFrame.of(klass.name(), poses);

            for (DisplaySlot slot : DisplaySlot.values()) {
                Vector3f wanted = originUnder(poses.get(slot));
                Vector3f shipped = originShippedAs(frames.get(slot));

                assertEquals(wanted.x, shipped.x, 1.0E-4F, klass + " " + slot + " x");
                assertEquals(wanted.y, shipped.y, 1.0E-4F, klass + " " + slot + " y");
                assertEquals(wanted.z, shipped.z, 1.0E-4F, klass + " " + slot + " z");
            }
        }
    }

    /**
     * The defect this path prevents. {@code ItemRenderBaseLegacy}'s GUI chain ends in
     * {@code translate(0, 11.3, -11.3)}, after the scale, so in the model's own rotated frame. A
     * {@code display{}} block applies its translation first, in slot space, so copying those three
     * numbers into the translation field is not the same transform. It is most of a slot out.
     */
    @Test
    void aTranslateAtTheEndOfAChainIsNotADisplayTranslation() {
        Vector3f frame = originUnder(Pose17.frame(Pose17.Klass.ITEM_RENDER_BASE_LEGACY, DisplaySlot.GUI));
        Matrix4f transcribed = new Matrix4f()
                .translation(0F, 11.3F / 16F, -11.3F / 16F)
                .rotate(new Quaternionf().rotationXYZ((float) Math.toRadians(30), (float) Math.toRadians(225), 0F))
                .scale(0.062F);
        Vector3f copied = transcribed.translate(-0.5F, -0.5F, -0.5F).transformPosition(new Vector3f());

        assertTrue(copied.y - frame.y > 0.75F,
                "the misordered transcription should sit most of a slot high, not " + (copied.y - frame.y));
    }

    /**
     * The two classes are close in the inventory and nowhere else, so a frame chosen by eye from a GUI
     * screenshot would pass and then be wrong in the hand.
     */
    @Test
    void theTwoClassesDifferOutsideTheInventory() {
        Vector3f base = originUnder(Pose17.frame(Pose17.Klass.ITEM_RENDER_BASE, DisplaySlot.HEAD));
        Vector3f legacy = originUnder(Pose17.frame(Pose17.Klass.ITEM_RENDER_BASE_LEGACY, DisplaySlot.HEAD));

        assertTrue(base.distance(legacy) > 0.5F, "head frames should not agree: " + base + " vs " + legacy);
        assertEquals(originUnder(Pose17.frame(Pose17.Klass.ITEM_RENDER_BASE, DisplaySlot.GUI)).y,
                originUnder(Pose17.frame(Pose17.Klass.ITEM_RENDER_BASE_LEGACY, DisplaySlot.GUI)).y, 0.02F);
    }

    /** {@code ItemTransform.apply} negates x, ry and rz for a left hand, so the file has to lead it. */
    @Test
    void aLeftHandFrameShipsPreNegated() {
        DisplayFrame frame = DisplayFrame.of("left", Pose17.frames(Pose17.Klass.ITEM_RENDER_BASE))
                .get(DisplaySlot.THIRD_PERSON_LEFT_HAND);
        Matrix4f unnegated = new Matrix4f()
                .translation((float) frame.translation().x / 16F, (float) frame.translation().y / 16F,
                        (float) frame.translation().z / 16F)
                .rotate(new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(frame.rotation().x),
                        (float) Math.toRadians(frame.rotation().y),
                        (float) Math.toRadians(frame.rotation().z)))
                .scale((float) frame.scale().x, (float) frame.scale().y, (float) frame.scale().z);

        assertNotEquals(frame.applied(), unnegated);
    }

    @Test
    void aShearedPoseIsRefused() {
        Matrix4f sheared = new Matrix4f(
                1F, 0F, 0F, 0F,
                0.4F, 1F, 0F, 0F,
                0F, 0F, 1F, 0F,
                0F, 0F, 0F, 1F);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> DisplayFrame.of("sheared", DisplaySlot.GUI, sheared));
        assertTrue(refused.getMessage().contains("no display{} form"), refused.getMessage());
    }

    @Test
    void aTranslationVanillaWouldClampIsRefused() {
        Matrix4f far = new Matrix4f().translation(0F, 40F, 0F);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> DisplayFrame.of("far", DisplaySlot.GUI, far));
        assertTrue(refused.getMessage().contains("clamps SILENTLY"), refused.getMessage());
    }

    @Test
    void aScaleVanillaWouldClampIsRefused() {
        Matrix4f huge = new Matrix4f().scale(9F);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> DisplayFrame.of("huge", DisplaySlot.GUI, huge));
        assertTrue(refused.getMessage().contains("clamps SILENTLY"), refused.getMessage());
    }

    /** An absent hand slot inherits the other hand's transform, so half a pair is never what was meant. */
    @Test
    void aHandSlotWithoutItsPartnerIsRefused() {
        Map<DisplaySlot, Matrix4f> half = new EnumMap<>(DisplaySlot.class);
        half.put(DisplaySlot.GUI, Pose17.frame(Pose17.Klass.ITEM_RENDER_BASE, DisplaySlot.GUI));
        half.put(DisplaySlot.FIRST_PERSON_RIGHT_HAND,
                Pose17.frame(Pose17.Klass.ITEM_RENDER_BASE, DisplaySlot.FIRST_PERSON_RIGHT_HAND));

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> DisplayFrame.of("half", half));
        assertTrue(refused.getMessage().contains("inherit the other hand's"), refused.getMessage());
    }

    /**
     * 1.7's full-3D hand pose scales y by -0.625: a sword in hand was the mirror of its model, and the frame
     * has to ship that mirror (signed scale) rather than quietly drop it. The plain pose is not mirrored, and
     * the two frames differ only in the third-person hands.
     */
    @Test
    void theFullThreeDHandPoseIsTheMirroredOne() {
        for (DisplaySlot slot : DisplaySlot.values()) {
            Matrix4f full = Pose17.frame(Pose17.Klass.ITEM_RENDER_WEAPON_FULL3D, slot);
            Matrix4f plain = Pose17.frame(Pose17.Klass.ITEM_RENDER_WEAPON, slot);
            boolean thirdPerson = slot == DisplaySlot.THIRD_PERSON_RIGHT_HAND || slot == DisplaySlot.THIRD_PERSON_LEFT_HAND;
            if (thirdPerson) {
                assertTrue(full.determinant3x3() < 0F, slot + " full-3D frame should mirror");
                assertTrue(plain.determinant3x3() > 0F, slot + " plain frame should not mirror");
                assertTrue(DisplayFrame.of("full3d", slot, full).scale().x < 0D, slot + " ships a signed scale");
            } else {
                assertEquals(plain, full, slot + " only the third-person hands differ");
            }
        }
        // 1.7: RenderPlayer translate(-1/16, 7/16, 1/16) then translate(0, 3/16, 0); the model origin then sits
        // at Forge's else-pose start in arm space: (-1/16, 7/16 + 3/16, 1/16) + scale(.625,-.625,.625)(0, -0.3, 0)
        Matrix4f toArm = new Matrix4f().rotateX((float) Math.toRadians(-90)).rotateY((float) Math.PI)
                .translate(-0.4375F, -0.375F, -1.125F);
        Vector3f forgeStart = toArm.mul(Pose17.frame(Pose17.Klass.ITEM_RENDER_WEAPON_FULL3D,
                DisplaySlot.THIRD_PERSON_RIGHT_HAND)).transformPosition(new Vector3f());
        Vector3f expected = new Matrix4f().translate(-0.0625F, 0.625F, 0.0625F).scale(0.625F, -0.625F, 0.625F)
                .rotateX((float) Math.toRadians(-100)).rotateY((float) Math.toRadians(45))
                .translate(0F, -0.3F, 0F).scale(1.5F).rotateY((float) Math.toRadians(50))
                .rotateZ((float) Math.toRadians(335)).translate(-0.9375F, -0.0625F, 0F)
                .transformPosition(new Vector3f());
        assertEquals(expected.x, forgeStart.x, 1.0E-5F);
        assertEquals(expected.y, forgeStart.y, 1.0E-5F);
        assertEquals(expected.z, forgeStart.z, 1.0E-5F);
    }

    @Test
    void posingNothingIsRefused() {
        assertThrows(IllegalStateException.class, () -> DisplayFrame.of("empty", Map.of()));
    }

    @Test
    void everyCallHandsBackItsOwnMatrix() {
        Matrix4f first = Pose17.frame(Pose17.Klass.ITEM_RENDER_BASE, DisplaySlot.GUI);
        Matrix4f second = Pose17.frame(Pose17.Klass.ITEM_RENDER_BASE, DisplaySlot.GUI);

        assertNotSame(first, second);
        assertEquals(first, second);

        first.translate(1F, 2F, 3F);
        assertEquals(second, Pose17.frame(Pose17.Klass.ITEM_RENDER_BASE, DisplaySlot.GUI));
    }
}
