package com.wf.gemrender.gltf.blend;

import com.wf.gemrender.gltf.NodeTable;

import static com.wf.gemrender.gltf.NodeTable.*;

/**
 * Decaying source-minus-target offset of one inertialized transition (Bollo, "Inertialization", GDC 2018).
 * Per channel: 1D quintic along the offset's direction at transition time; velocity projected onto it,
 * clamped so the offset never grows; {@code a0 >= 0} so it never overshoots.
 */
final class Inertialization {
    private static final int COEFFS = 7;
    private static final int X0 = 0, V0 = 1, HALF_A0 = 2, A = 3, B = 4, C = 5, T1 = 6;
    private static final int T_DIR = 0, R_AXIS = 3, S_DIR = 6, T_CURVE = 9, R_CURVE = 9 + COEFFS,
            S_CURVE = 9 + 2 * COEFFS, NODE_FLOATS = 9 + 3 * COEFFS;
    private static final float EPSILON = 1.0e-6f;

    private final NodeTable table;
    private final float[] nodes;
    private final float[] morphs;
    private final int[] morphSlots;
    private final float[] q = new float[4];
    private int generation;
    private float longest;

    Inertialization(NodeTable table) {
        this.table = table;
        this.nodes = new float[table.nodeCount() * NODE_FLOATS];
        int weights = table.scratchFloats() - table.nodeCount() * TRS_STRIDE;
        this.morphs = new float[weights * COEFFS];
        this.morphSlots = new int[weights];
        for (int m = 0; m < weights; m++) {
            morphSlots[m] = table.slotOfOffset(table.nodeCount() * TRS_STRIDE + m);
        }
    }

    void checkFits(NodeTable evaluated) {
        if (evaluated != table) {
            throw new IllegalArgumentException("crossfade built for another model's node table");
        }
    }

    int generation() {
        return generation;
    }

    boolean activeAt(float elapsed) {
        return elapsed < longest;
    }

    /**
     * Offsets {@code source(now) - target(now)}, velocity from {@code source(now - h)} against the same
     * target; {@code h < 0} => forward difference.
     */
    void begin(float[] sourceNow, float[] sourcePrev, float[] target, float h, float duration) {
        generation++;
        longest = 0.0f;
        int count = table.nodeCount();
        for (int n = 0; n < count; n++) {
            int s = n * TRS_STRIDE;
            int o = n * NODE_FLOATS;
            longest = Math.max(longest, vector(sourceNow, sourcePrev, target, s + TRANSLATION, o + T_DIR,
                    o + T_CURVE, h, duration));
            longest = Math.max(longest, vector(sourceNow, sourcePrev, target, s + SCALE, o + S_DIR,
                    o + S_CURVE, h, duration));
            longest = Math.max(longest, rotation(sourceNow, sourcePrev, target, s + ROTATION, o, h, duration));
        }
        int base = count * TRS_STRIDE;
        for (int m = 0; m < morphs.length / COEFFS; m++) {
            float x0 = sourceNow[base + m] - target[base + m];
            float xp = sourcePrev[base + m] - target[base + m];
            float sign = x0 < 0.0f ? -1.0f : 1.0f;
            longest = Math.max(longest, curve(morphs, m * COEFFS, sign * x0, sign * (x0 - xp) / h, duration));
            morphs[m * COEFFS + X0] *= sign;
            scaleCurve(morphs, m * COEFFS, sign);
        }
    }

    private float vector(float[] now, float[] prev, float[] target, int s, int dir, int curve, float h,
                         float duration) {
        float x = now[s] - target[s], y = now[s + 1] - target[s + 1], z = now[s + 2] - target[s + 2];
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length < EPSILON) {
            nodes[curve + T1] = 0.0f;
            return 0.0f;
        }
        x /= length;
        y /= length;
        z /= length;
        nodes[dir] = x;
        nodes[dir + 1] = y;
        nodes[dir + 2] = z;
        float prevAlong = (prev[s] - target[s]) * x + (prev[s + 1] - target[s + 1]) * y
                + (prev[s + 2] - target[s + 2]) * z;
        return curve(nodes, curve, length, (length - prevAlong) / h, duration);
    }

    private float rotation(float[] now, float[] prev, float[] target, int s, int o, float h, float duration) {
        float tx = -target[s], ty = -target[s + 1], tz = -target[s + 2], tw = target[s + 3];
        multiply(now[s], now[s + 1], now[s + 2], now[s + 3], tx, ty, tz, tw, q);
        float angle = axisAngle(q);
        if (angle < EPSILON) {
            nodes[o + R_CURVE + T1] = 0.0f;
            return 0.0f;
        }
        float ax = q[0], ay = q[1], az = q[2];
        nodes[o + R_AXIS] = ax;
        nodes[o + R_AXIS + 1] = ay;
        nodes[o + R_AXIS + 2] = az;
        multiply(prev[s], prev[s + 1], prev[s + 2], prev[s + 3], tx, ty, tz, tw, q);
        float prevAngle = axisAngle(q);
        float prevAlong = prevAngle * (q[0] * ax + q[1] * ay + q[2] * az);
        return curve(nodes, o + R_CURVE, angle, (angle - prevAlong) / h, duration);
    }

    /**
     * Unit axis into {@code q[0..2]}, angle in {@code [0, pi]} returned; shortest arc.
     */
    private static float axisAngle(float[] q) {
        float x = q[0], y = q[1], z = q[2], w = q[3];
        if (w < 0.0f) {
            x = -x;
            y = -y;
            z = -z;
            w = -w;
        }
        float sin = (float) Math.sqrt(x * x + y * y + z * z);
        if (sin < EPSILON) {
            q[0] = 1.0f;
            q[1] = 0.0f;
            q[2] = 0.0f;
            return 0.0f;
        }
        q[0] = x / sin;
        q[1] = y / sin;
        q[2] = z / sin;
        return 2.0f * (float) Math.atan2(sin, w);
    }

    static void multiply(float ax, float ay, float az, float aw, float bx, float by, float bz, float bw,
                         float[] out) {
        out[0] = aw * bx + ax * bw + ay * bz - az * by;
        out[1] = aw * by - ax * bz + ay * bw + az * bx;
        out[2] = aw * bz + ax * by - ay * bx + az * bw;
        out[3] = aw * bw - ax * bx - ay * by - az * bz;
    }

    /**
     * Quintic from {@code x0 > 0}, {@code v0}; returns its end time.
     */
    static float curve(float[] c, int o, float x0, float v0, float duration) {
        if (x0 < EPSILON || duration <= 0.0f) {
            c[o + T1] = 0.0f;
            return 0.0f;
        }
        v0 = Math.min(v0, 0.0f);
        float t1 = duration;
        if (v0 < 0.0f) {
            t1 = Math.min(t1, -5.0f * x0 / v0);
        }
        float t2 = t1 * t1;
        float a0 = Math.max(0.0f, (-8.0f * v0 * t1 - 20.0f * x0) / t2);
        c[o + X0] = x0;
        c[o + V0] = v0;
        c[o + HALF_A0] = 0.5f * a0;
        c[o + A] = -(a0 * t2 + 6.0f * v0 * t1 + 12.0f * x0) / (2.0f * t2 * t2 * t1);
        c[o + B] = (3.0f * a0 * t2 + 16.0f * v0 * t1 + 30.0f * x0) / (2.0f * t2 * t2);
        c[o + C] = -(3.0f * a0 * t2 + 12.0f * v0 * t1 + 20.0f * x0) / (2.0f * t2 * t1);
        c[o + T1] = t1;
        return t1;
    }

    private static void scaleCurve(float[] c, int o, float sign) {
        c[o + V0] *= sign;
        c[o + HALF_A0] *= sign;
        c[o + A] *= sign;
        c[o + B] *= sign;
        c[o + C] *= sign;
    }

    static float value(float[] c, int o, float t) {
        if (t >= c[o + T1]) {
            return 0.0f;
        }
        return (((((c[o + A] * t + c[o + B]) * t + c[o + C]) * t + c[o + HALF_A0]) * t + c[o + V0]) * t) + c[o + X0];
    }

    /**
     * {@code out += offset(elapsed)} scaled by {@code weight * mask}.
     */
    void apply(float[] out, float elapsed, float weight, BlendMask mask) {
        int count = table.nodeCount();
        for (int n = 0; n < count; n++) {
            float e = weight * mask.weight(n);
            if (e <= 0.0f || !table.isPosable(n)) {
                continue;
            }
            int s = n * TRS_STRIDE;
            int o = n * NODE_FLOATS;
            float t = e * value(nodes, o + T_CURVE, elapsed);
            if (t != 0.0f) {
                out[s + TRANSLATION] += nodes[o + T_DIR] * t;
                out[s + TRANSLATION + 1] += nodes[o + T_DIR + 1] * t;
                out[s + TRANSLATION + 2] += nodes[o + T_DIR + 2] * t;
            }
            float sc = e * value(nodes, o + S_CURVE, elapsed);
            if (sc != 0.0f) {
                out[s + SCALE] += nodes[o + S_DIR] * sc;
                out[s + SCALE + 1] += nodes[o + S_DIR + 1] * sc;
                out[s + SCALE + 2] += nodes[o + S_DIR + 2] * sc;
            }
            float angle = e * value(nodes, o + R_CURVE, elapsed);
            if (angle != 0.0f) {
                float half = 0.5f * angle;
                float sin = (float) Math.sin(half);
                int r = s + ROTATION;
                multiply(nodes[o + R_AXIS] * sin, nodes[o + R_AXIS + 1] * sin, nodes[o + R_AXIS + 2] * sin,
                        (float) Math.cos(half), out[r], out[r + 1], out[r + 2], out[r + 3], q);
                out[r] = q[0];
                out[r + 1] = q[1];
                out[r + 2] = q[2];
                out[r + 3] = q[3];
            }
        }
        int base = count * TRS_STRIDE;
        for (int m = 0; m < morphSlots.length; m++) {
            float e = weight * mask.weight(morphSlots[m]);
            if (e > 0.0f) {
                out[base + m] += e * value(morphs, m * COEFFS, elapsed);
            }
        }
    }
}
