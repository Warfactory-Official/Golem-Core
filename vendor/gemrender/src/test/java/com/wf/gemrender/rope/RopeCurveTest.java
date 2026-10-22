package com.wf.gemrender.rope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RopeCurveTest {
    private final Vector3f scratch = new Vector3f();

    @Test
    @DisplayName("a rope with no sag is the straight line between its ends")
    void tautIsStraight() {
        for (float t = 0.0f; t <= 1.0f; t += 0.125f) {
            RopeCurve.point(scratch, 0.0f, 4.0f, 0.0f, 3.0f, 1.0f, -2.0f, 0.0f, t);

            assertThat(scratch.x).isCloseTo(3.0f * t, within(1.0e-5f));
            assertThat(scratch.y).isCloseTo(4.0f - 3.0f * t, within(1.0e-5f));
            assertThat(scratch.z).isCloseTo(-2.0f * t, within(1.0e-5f));
        }
    }

    @Test
    @DisplayName("both ends stay put however much it sags")
    void endsArePinned() {
        for (float sag : new float[] {0.0f, 0.5f, 4.0f, 40.0f}) {
            RopeCurve.point(scratch, 1.0f, 2.0f, 3.0f, -4.0f, 5.0f, 6.0f, sag, 0.0f);
            assertThat(scratch).isEqualTo(new Vector3f(1.0f, 2.0f, 3.0f));

            RopeCurve.point(scratch, 1.0f, 2.0f, 3.0f, -4.0f, 5.0f, 6.0f, sag, 1.0f);
            assertThat(scratch).isEqualTo(new Vector3f(-4.0f, 5.0f, 6.0f));
        }
    }

    @Test
    @DisplayName("the sag is measured down from the chord, at its deepest")
    void sagIsDepthBelowTheChord() {
        // Level ends, so the deepest point is the middle and the chord there is the endpoints' height.
        RopeCurve.point(scratch, -2.0f, 10.0f, 0.0f, 2.0f, 10.0f, 0.0f, 1.5f, 0.5f);

        assertThat(scratch.y).isCloseTo(8.5f, within(1.0e-5f));
        assertThat(RopeCurve.lowestY(10.0f, 10.0f, 1.5f)).isCloseTo(8.5f, within(1.0e-5f));
    }

    @Test
    @DisplayName("the lowest point of a sloped rope is not its middle")
    void lowestPointFollowsTheSlope() {
        float ay = 12.0f;
        float by = 4.0f;
        float sag = 3.0f;

        float lowest = RopeCurve.lowestY(ay, by, sag);

        // Sample densely and let the curve say where its own minimum is.
        float sampled = Float.MAX_VALUE;
        for (int i = 0; i <= 2000; i++) {
            float t = i / 2000.0f;
            RopeCurve.point(scratch, 0.0f, ay, 0.0f, 6.0f, by, 0.0f, sag, t);
            sampled = Math.min(sampled, scratch.y);
        }

        assertThat(lowest).isCloseTo(sampled, within(1.0e-3f));
        assertThat(lowest).isLessThan(Math.min(ay, by));
    }

    @Test
    @DisplayName("a straight rope's length is the distance between its ends")
    void straightLength() {
        float length = RopeCurve.length(0.0f, 0.0f, 0.0f, 3.0f, 4.0f, 0.0f, 0.0f);

        assertThat(length).isCloseTo(5.0f, within(1.0e-4f));
        assertThat(length).isCloseTo(RopeCurve.chord(0.0f, 0.0f, 0.0f, 3.0f, 4.0f, 0.0f), within(1.0e-4f));
    }

    @Test
    @DisplayName("length agrees with walking the curve in small steps")
    void lengthAgreesWithASum() {
        float ax = -3.0f;
        float ay = 7.0f;
        float az = 1.0f;
        float bx = 5.0f;
        float by = 2.0f;
        float bz = -4.0f;
        float sag = 2.75f;

        Vector3f previous = RopeCurve.point(new Vector3f(), ax, ay, az, bx, by, bz, sag, 0.0f);
        Vector3f current = new Vector3f();

        double walked = 0.0;
        int steps = 20000;
        for (int i = 1; i <= steps; i++) {
            RopeCurve.point(current, ax, ay, az, bx, by, bz, sag, (float) i / steps);
            walked += current.distance(previous);
            previous.set(current);
        }

        assertThat(RopeCurve.length(ax, ay, az, bx, by, bz, sag)).isCloseTo((float) walked, within(1.0e-3f));
    }

    @Test
    @DisplayName("solving for a length gives a rope of that length")
    void sagSolvesForLength() {
        float ax = 0.0f;
        float ay = 20.0f;
        float az = 0.0f;
        float bx = 7.0f;
        float by = 18.0f;
        float bz = -3.0f;

        for (float wanted : new float[] {8.0f, 10.0f, 16.0f, 64.0f}) {
            float sag = RopeCurve.sagForLength(ax, ay, az, bx, by, bz, wanted);

            assertThat(RopeCurve.length(ax, ay, az, bx, by, bz, sag))
                    .as("rope asked to be %s long", wanted)
                    .isCloseTo(wanted, within(1.0e-3f));
        }
    }

    @Test
    @DisplayName("a rope cannot be shorter than the distance it spans, and does not try")
    void tooShortIsTaut() {
        float chord = RopeCurve.chord(0.0f, 0.0f, 0.0f, 0.0f, 10.0f, 0.0f);

        assertThat(RopeCurve.sagForLength(0.0f, 0.0f, 0.0f, 0.0f, 10.0f, 0.0f, chord - 1.0f)).isZero();
        assertThat(RopeCurve.sagForLength(0.0f, 0.0f, 0.0f, 0.0f, 10.0f, 0.0f, chord)).isZero();
        assertThat(RopeCurve.sagForSlack(0.0f, 0.0f, 0.0f, 0.0f, 10.0f, 0.0f, 0.5f)).isZero();
    }

    @Test
    @DisplayName("slack is a multiple of the straight-line distance")
    void slackIsRelative() {
        float sag = RopeCurve.sagForSlack(-4.0f, 9.0f, 2.0f, 4.0f, 9.0f, 2.0f, 1.25f);

        assertThat(RopeCurve.length(-4.0f, 9.0f, 2.0f, 4.0f, 9.0f, 2.0f, sag))
                .isCloseTo(8.0f * 1.25f, within(1.0e-3f));
    }

    @Test
    @DisplayName("slack means nothing on a vertical rope, and this is what it does instead")
    void slackOnAVerticalRopeIsIllConditioned() {
        // Pinned rather than fixed. The sag is along -Y, so on a rope hanging straight down it slides
        // points along the rope instead of away from it: the length is barely sensitive to it until
        // the curve doubles back on itself, so the solve has to reach far to buy a little length.
        float sag = RopeCurve.sagForSlack(0.0f, 20.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.01f);

        assertThat(RopeCurve.length(0.0f, 20.0f, 0.0f, 0.0f, 0.0f, 0.0f, sag))
                .as("the length it was asked for is the length it gets")
                .isCloseTo(20.2f, within(1.0e-2f));

        assertThat(sag).as("but at a sag of a quarter of its own length").isGreaterThan(5.0f);

        // And the damage that does: the midpoint of the rope is nowhere near the midpoint of the drop,
        // so a texture that tiles along it bunches up at the bottom.
        RopeCurve.point(scratch, 0.0f, 20.0f, 0.0f, 0.0f, 0.0f, 0.0f, sag, 0.5f);
        assertThat(scratch.y).isLessThan(6.0f);

        // Which is why anything vertical sets the sag itself. At zero it is evenly spaced.
        RopeCurve.point(scratch, 0.0f, 20.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f);
        assertThat(scratch.y).isCloseTo(10.0f, within(1.0e-5f));
    }

    @Test
    @DisplayName("a vertical rope has a frame, which is the case a catenary has no answer for")
    void verticalRopeHasAFrame() {
        // A mine moored to the sea floor is exactly this: the two ends share a column.
        Vector3f[] basis = {new Vector3f(), new Vector3f(), new Vector3f()};

        RopeCurve.frame(0.0f, 40.0f, 0.0f, 0.0f, 12.0f, 0.0f, 0.0f, 0.5f, basis);

        assertThat(basis[0].length()).isCloseTo(1.0f, within(1.0e-5f));
        assertThat(basis[1].length()).isCloseTo(1.0f, within(1.0e-5f));
        assertThat(basis[2].length()).isCloseTo(1.0f, within(1.0e-5f));

        assertThat(basis[0].dot(basis[1])).isCloseTo(0.0f, within(1.0e-5f));
        assertThat(basis[0].dot(basis[2])).isCloseTo(0.0f, within(1.0e-5f));
        assertThat(basis[1].dot(basis[2])).isCloseTo(0.0f, within(1.0e-5f));
    }

    @Test
    @DisplayName("the frame is orthonormal everywhere along every rope")
    void frameIsOrthonormal() {
        float[][] ropes = {
                {0.0f, 0.0f, 0.0f, 10.0f, 0.0f, 0.0f, 0.0f},
                {0.0f, 0.0f, 0.0f, 10.0f, 0.0f, 0.0f, 3.0f},
                {0.0f, 30.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f},
                {0.0f, 30.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.4f},
                {-2.0f, 8.0f, 3.0f, 6.0f, 1.0f, -5.0f, 1.5f},
                {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f},
        };

        Vector3f[] basis = {new Vector3f(), new Vector3f(), new Vector3f()};

        for (float[] rope : ropes) {
            for (int i = 0; i <= 16; i++) {
                float t = i / 16.0f;
                RopeCurve.frame(rope[0], rope[1], rope[2], rope[3], rope[4], rope[5], rope[6], t, basis);

                for (Vector3f axis : basis) {
                    assertThat(axis.length()).as("axis length at t=%s", t).isCloseTo(1.0f, within(1.0e-4f));
                    assertThat(Float.isNaN(axis.x) || Float.isNaN(axis.y) || Float.isNaN(axis.z)).isFalse();
                }

                assertThat(basis[0].dot(basis[1])).isCloseTo(0.0f, within(1.0e-4f));
                assertThat(basis[1].dot(basis[2])).isCloseTo(0.0f, within(1.0e-4f));
                assertThat(basis[0].dot(basis[2])).isCloseTo(0.0f, within(1.0e-4f));
            }
        }
    }

    @Test
    @DisplayName("the cull sphere contains the whole rope, thickness and sway included")
    void sphereContainsTheRope() {
        float ax = -3.0f;
        float ay = 30.0f;
        float az = 2.0f;
        float bx = 4.0f;
        float by = 22.0f;
        float bz = -6.0f;
        float sag = 5.0f;
        float margin = 0.35f;

        Vector4f sphere = new Vector4f();
        RopeCurve.sphere(sphere, ax, ay, az, bx, by, bz, sag, margin);

        Vector3f centre = new Vector3f(sphere.x, sphere.y, sphere.z);
        for (int i = 0; i <= 512; i++) {
            RopeCurve.point(scratch, ax, ay, az, bx, by, bz, sag, i / 512.0f);

            // Every point of the surface is within `margin` of the curve, in any direction.
            assertThat(scratch.distance(centre) + margin)
                    .as("point at t=%s", i / 512.0f)
                    .isLessThanOrEqualTo(sphere.w + 1.0e-4f);
        }
    }

    @Test
    @DisplayName("the lowest point of a taut rope is its lower end, not its middle")
    void tautHangsLowestAtItsEnd() {
        assertThat(RopeCurve.lowestY(149.5f, 132.0f, 0.0f)).isCloseTo(132.0f, within(1.0e-5f));
        assertThat(RopeCurve.lowestY(132.0f, 149.5f, 0.0f)).isCloseTo(132.0f, within(1.0e-5f));
    }

    @Test
    @DisplayName("the cull sphere contains a taut vertical rope, which is what a mooring is")
    void sphereContainsAMooring() {
        float x = 100.5f;
        float z = 100.5f;
        float top = 149.5f;
        float bottom = 132.0f;
        float margin = 0.15f;

        Vector4f sphere = new Vector4f();
        RopeCurve.sphere(sphere, x, top, z, x, bottom, z, 0.0f, margin);

        // Half the rope's length plus the margin, centred on its middle. Before the taut case was
        // handled this was half that, about the middle of the TOP half, so the bottom half of every
        // mooring chain in the world sat outside its own cull sphere.
        assertThat(sphere.y).isCloseTo((top + bottom) * 0.5f, within(1.0e-4f));
        assertThat(sphere.w).isCloseTo((top - bottom) * 0.5f + margin, within(1.0e-4f));

        Vector3f centre = new Vector3f(sphere.x, sphere.y, sphere.z);
        for (int i = 0; i <= 512; i++) {
            RopeCurve.point(scratch, x, top, z, x, bottom, z, 0.0f, i / 512.0f);
            assertThat(scratch.distance(centre) + margin)
                    .as("point at t=%s", i / 512.0f)
                    .isLessThanOrEqualTo(sphere.w + 1.0e-4f);
        }
    }
}
