package dev.golem.core.render.display;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * One display slot's transform in the form vanilla stores it, decomposed from the matrix it stands for.
 *
 * <p>A {@code display{}} block cannot hold a matrix. It holds a translate/rotate/scale triple, applies it as
 * {@code T . Rxyz . S}, and applies it whatever it says — a pose that is not of that form ships wrong
 * rather than failing. Three more traps sit beside that: {@code ItemTransform.Deserializer} clamps
 * translation and scale <em>silently</em>, {@code apply} negates x, ry and rz for the left hands whatever
 * the file says, and a hand slot left out inherits its partner's transform rather than staying unposed.
 *
 * <p>So this type exists to make all four loud. {@link #of(String, Map)} decomposes, rounds to what will
 * actually be written, rebuilds the triple, and throws if the result misses the pose it came from or
 * trips a clamp. What survives is a frame that vanilla will reproduce exactly.
 *
 * <p>The three triples are doubles because that is what a JSON number is, and they are already rounded
 * to what will be written: the residual is measured against the shipped decimals, not against the full
 * precision the decomposition happened to reach.
 *
 * @param slot        the display context this transform belongs to
 * @param rotation    XYZ Euler angles in degrees, as shipped — pre-negated in y and z for a left hand
 * @param translation in <b>pixels</b>, the unit the JSON uses, pre-negated in x for a left hand
 * @param scale       the scale factor per axis, signed together when the pose mirrors
 * @param residual    the worst element by which {@link #applied()} misses the pose, over the pose's scale
 */
public record DisplayFrame(DisplaySlot slot, Vector3d rotation, Vector3d translation, Vector3d scale,
                           float residual) {

    /** {@code ItemTransform.Deserializer}'s silent translation clamp, in blocks. */
    public static final float MAX_TRANSLATION = 5F;

    /** {@code ItemTransform.Deserializer}'s silent scale clamp. */
    public static final float MAX_SCALE = 4F;

    /** How far a shipped triple may miss the pose it was decomposed from, as a fraction of its scale. */
    public static final float MAX_RESIDUAL = 1.0E-3F;

    /**
     * The factor the shipped decimals are rounded on — six places. A triple is gated on what it will be
     * written as, not on what it was. Six rather than five because a chain of fifteen float ops lands a
     * uniform scale a few ulps off, and at five the axes round to different decimals, so a uniform scale
     * ships looking non-uniform.
     */
    private static final double ROUNDING = 1000000D;

    /** Below this |cos b| the x and z rotations are one degree of freedom and the split is arbitrary. */
    private static final float GIMBAL_EPSILON = 1.0E-6F;

    /**
     * The frames a set of poses ships as, checked against each other as well as individually.
     *
     * @param id    what to name in a failure — the model or item these poses belong to
     * @param poses the model-to-slot matrix wanted in each display context
     */
    public static Map<DisplaySlot, DisplayFrame> of(String id, Map<DisplaySlot, Matrix4f> poses) {
        if (poses.isEmpty()) {
            throw new IllegalStateException(id + " states a pose for no display slot at all, so it would "
                    + "ship as an unposed item behind a green run");
        }
        requireHandPair(id, poses.keySet(), DisplaySlot.THIRD_PERSON_RIGHT_HAND);
        requireHandPair(id, poses.keySet(), DisplaySlot.FIRST_PERSON_RIGHT_HAND);

        Map<DisplaySlot, DisplayFrame> frames = new EnumMap<>(DisplaySlot.class);
        for (DisplaySlot slot : DisplaySlot.values()) {
            Matrix4f pose = poses.get(slot);
            if (pose != null) {
                frames.put(slot, of(id, slot, pose));
            }
        }
        return frames;
    }

    /**
     * One pose's frame.
     *
     * <p>The engine leaves the pose at {@code A . T(-0.5)} before the model draws — vanilla's own
     * recentring of a model authored in the 0..1 box — so the triple that reproduces a frame {@code F} is
     * {@code A = T(-0.5) . F . T(+0.5)}.
     */
    public static DisplayFrame of(String id, DisplaySlot slot, Matrix4f pose) {
        Matrix4f wanted = new Matrix4f().translation(-0.5F, -0.5F, -0.5F)
                .mul(pose)
                .translate(0.5F, 0.5F, 0.5F);

        Vector3f scale = scaleOf(wanted);
        Vector3f translation = new Vector3f(wanted.m30(), wanted.m31(), wanted.m32());
        Vector3f rotation = rotationOf(wanted, scale);

        check(id, slot, "translation", translation, MAX_TRANSLATION);
        check(id, slot, "scale", scale, MAX_SCALE);
        if (slot.leftHand()) {
            rotation = new Vector3f(rotation.x, -rotation.y, -rotation.z);
            translation = new Vector3f(-translation.x, translation.y, translation.z);
        }

        DisplayFrame frame = new DisplayFrame(slot,
                round(rotation), round(new Vector3f(translation).mul(16F)), round(scale), 0F);

        float residual = miss(wanted, frame.applied());
        if (residual > MAX_RESIDUAL) {
            throw new IllegalStateException(id + " " + slot.serializedName() + ": this pose has no "
                    + "display{} form - rebuilding vanilla's translate/rotate/scale triple from it misses "
                    + "the pose by " + residual + " of the pose's own scale, over the " + MAX_RESIDUAL
                    + " limit. A sheared basis is not a rotate-then-scale and vanilla applies the triple "
                    + "regardless, so the item would draw wrong rather than fail");
        }
        return new DisplayFrame(slot, frame.rotation, frame.translation, frame.scale, residual);
    }

    /**
     * The matrix {@code ItemTransform.apply} builds from this triple, left-hand negation included. Narrow
     * to float first: vanilla stores these as floats, so that is the arithmetic the game will do.
     */
    public Matrix4f applied() {
        float sign = slot.leftHand() ? -1F : 1F;
        return new Matrix4f()
                .translation(sign * (float) translation.x / 16F, (float) translation.y / 16F,
                        (float) translation.z / 16F)
                .rotate(new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(rotation.x),
                        (float) Math.toRadians(sign * rotation.y),
                        (float) Math.toRadians(sign * rotation.z)))
                .scale((float) scale.x, (float) scale.y, (float) scale.z);
    }

    private static void requireHandPair(String id, Collection<DisplaySlot> slots, DisplaySlot right) {
        DisplaySlot left = right.partner();
        if (slots.contains(right) == slots.contains(left)) {
            return;
        }
        DisplaySlot stated = slots.contains(right) ? right : left;
        throw new IllegalStateException(id + " poses " + stated.serializedName() + " without "
                + stated.partner().serializedName() + "; the absent slot would inherit the other hand's "
                + "transform rather than stay unposed");
    }

    private static void check(String id, DisplaySlot slot, String what, Vector3f value, float limit) {
        float worst = Math.max(Math.abs(value.x), Math.max(Math.abs(value.y), Math.abs(value.z)));
        if (worst > limit) {
            throw new IllegalStateException(id + " " + slot.serializedName() + ": " + what + " " + value
                    + " exceeds vanilla's " + limit + " limit, which ItemTransform clamps SILENTLY - the "
                    + "pose would ship wrong rather than fail");
        }
    }

    /** Column lengths, signed together when the basis is mirrored: an odd number of negatives. */
    private static Vector3f scaleOf(Matrix4fc m) {
        float sx = length(m.m00(), m.m01(), m.m02());
        float sy = length(m.m10(), m.m11(), m.m12());
        float sz = length(m.m20(), m.m21(), m.m22());
        return m.determinant3x3() < 0F ? new Vector3f(-sx, -sy, -sz) : new Vector3f(sx, sy, sz);
    }

    /**
     * A column's length, summed in double. A uniform scale whose squares are added in float comes back
     * a few ulps apart per axis, which then rounds to visibly different decimals in the shipped file.
     */
    private static float length(float x, float y, float z) {
        return (float) Math.sqrt((double) x * x + (double) y * y + (double) z * z);
    }

    /**
     * The XYZ Euler triple, in closed form and then in its other spelling: every rotation has two, and
     * the smaller one is both the readable one and usually the one the source was written in.
     */
    private static Vector3f rotationOf(Matrix4fc m, Vector3f scale) {
        float r00 = m.m00() / scale.x, r01 = m.m10() / scale.y, r02 = m.m20() / scale.z;
        float r10 = m.m01() / scale.x, r11 = m.m11() / scale.y, r12 = m.m21() / scale.z;
        float r22 = m.m22() / scale.z;

        float cosB = (float) Math.sqrt(r00 * r00 + r01 * r01);
        float b = (float) Math.atan2(r02, cosB);
        float a;
        float c;
        if (cosB > GIMBAL_EPSILON) {
            a = (float) Math.atan2(-r12, r22);
            c = (float) Math.atan2(-r01, r00);
        } else {
            a = Math.signum(r02) * (float) Math.atan2(r10, r11);
            c = 0F;
        }

        Vector3f principal = new Vector3f(wrap((float) Math.toDegrees(a)), wrap((float) Math.toDegrees(b)),
                wrap((float) Math.toDegrees(c)));
        Vector3f alternate = new Vector3f(wrap(principal.x + 180F), wrap(180F - principal.y),
                wrap(principal.z + 180F));
        return magnitude(alternate) < magnitude(principal) ? alternate : principal;
    }

    private static float magnitude(Vector3f angles) {
        return Math.abs(angles.x) + Math.abs(angles.y) + Math.abs(angles.z);
    }

    private static float wrap(float degrees) {
        float wrapped = degrees % 360F;
        if (wrapped > 180F) {
            wrapped -= 360F;
        }
        if (wrapped <= -180F) {
            wrapped += 360F;
        }
        return wrapped;
    }

    /**
     * To the decimals that will be written, as the double a JSON number is. Rounding to a float instead
     * would ship a value whose text form reads back as a different number, which any check comparing the
     * file against the declaration would then report as a mismatch.
     */
    private static Vector3d round(Vector3f value) {
        return new Vector3d(Math.round(value.x * ROUNDING) / ROUNDING,
                Math.round(value.y * ROUNDING) / ROUNDING,
                Math.round(value.z * ROUNDING) / ROUNDING);
    }

    /** The worst element by which two frames disagree, over the scale they are expressed at. */
    private static float miss(Matrix4fc wanted, Matrix4fc got) {
        float[] a = new float[16];
        float[] b = new float[16];
        wanted.get(a);
        got.get(b);
        float worst = 0F;
        float magnitude = 0F;
        for (int element = 0; element < 16; element++) {
            worst = Math.max(worst, Math.abs(a[element] - b[element]));
            magnitude = Math.max(magnitude, Math.abs(a[element]));
        }
        float relative = magnitude > 0F ? worst / magnitude : worst;
        return Float.isFinite(relative) ? relative : Float.MAX_VALUE;
    }
}
